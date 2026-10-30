package com.juniverse.cqrs.projection;

import com.juniverse.cqrs.config.DatabaseSchemaInitializer;
import com.juniverse.cqrs.event.DomainEvent;
import com.juniverse.cqrs.event.EventStore;
import com.juniverse.cqrs.event.LineItem;
import com.juniverse.cqrs.event.OrderCancelled;
import com.juniverse.cqrs.event.OrderConfirmed;
import com.juniverse.cqrs.event.OrderCreated;
import com.juniverse.cqrs.event.StoredEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El <b>proyector</b>: construye las vistas de lectura (MySQL) a partir de los eventos (PostgreSQL).
 *
 * <p>Cada tanda de eventos se aplica en UNA transacción de MySQL junto con la actualización del <i>checkpoint</i> (hasta qué
 * evento se ha procesado). Por eso, si algo falla, no se aplica nada de la tanda y el checkpoint no avanza: cada evento se
 * proyecta exactamente una vez. Además la fila del checkpoint se bloquea ({@code FOR UPDATE}), de modo que dos instancias
 * no proyectan a la vez.</p>
 *
 * <p>Las vistas son <b>desechables</b>: se pueden borrar y reconstruir leyendo de nuevo todos los eventos
 * ({@link #rebuild()}).</p>
 */
@Component
public class OrderProjector {

    private static final Logger LOG = LoggerFactory.getLogger(OrderProjector.class);
    private static final String CHECKPOINT = "orders";

    private final JdbcClient read;
    private final TransactionTemplate tx;
    private final EventStore events;
    private final CqrsProperties properties;

    /**
     * Crea el proyector.
     *
     * @param read cliente de la base de lectura
     * @param tx plantilla de transacciones de MySQL
     * @param events registro de eventos
     * @param properties propiedades de CQRS
     * @param schema garantiza que las tablas existen antes de usar la base de lectura
     */
    public OrderProjector(@Qualifier("readJdbc") JdbcClient read, @Qualifier("readTransactionTemplate") TransactionTemplate tx,
            EventStore events, CqrsProperties properties, DatabaseSchemaInitializer schema) {
        this.read = read;
        this.tx = tx;
        this.events = events;
        this.properties = properties;
        read.sql("insert ignore into projection_checkpoint (name, last_seq) values (:name, 0)").param("name", CHECKPOINT).update();
    }

    /**
     * Proyecta todos los eventos pendientes, por tandas.
     *
     * @return cuántos eventos se aplicaron
     */
    public int catchUp() {
        int total = 0;
        while (true) {
            Integer processed = tx.execute(status -> processBatch());
            total += processed == null ? 0 : processed;
            if (processed == null || processed < properties.batchSize()) {
                return total;
            }
        }
    }

    private int processBatch() {
        long lastSeq = read.sql("select last_seq from projection_checkpoint where name = :name for update").param("name", CHECKPOINT)
                .query(Long.class).single();
        List<StoredEvent> batch = events.readAfter(lastSeq, properties.batchSize());
        for (StoredEvent stored : batch) {
            apply(stored);
        }
        if (!batch.isEmpty()) {
            read.sql("update projection_checkpoint set last_seq = :seq where name = :name")
                    .param("seq", batch.get(batch.size() - 1).seq()).param("name", CHECKPOINT).update();
        }
        return batch.size();
    }

    /**
     * Número de secuencia hasta el que se ha proyectado.
     *
     * @return último {@code seq} aplicado
     */
    public long projectedSeq() {
        return read.sql("select last_seq from projection_checkpoint where name = :name").param("name", CHECKPOINT).query(Long.class).single();
    }

    /**
     * Borra todas las vistas y vuelve a proyectar los eventos desde el principio.
     *
     * @return cuántos eventos se reaplicaron
     */
    public int rebuild() {
        tx.executeWithoutResult(status -> {
            read.sql("select last_seq from projection_checkpoint where name = :name for update").param("name", CHECKPOINT).query(Long.class).single();
            for (String table : List.of("order_line_read", "order_summary", "customer_stats", "product_sales")) {
                read.sql("delete from " + table).update();
            }
            read.sql("update projection_checkpoint set last_seq = 0 where name = :name").param("name", CHECKPOINT).update();
        });
        int applied = catchUp();
        LOG.info("Vistas reconstruidas a partir de {} evento(s)", applied);
        return applied;
    }

    private void apply(StoredEvent stored) {
        LocalDateTime when = stored.occurredAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        DomainEvent event = stored.event();
        switch (event) {
            case OrderCreated created -> onCreated(created, when);
            case OrderConfirmed confirmed -> onConfirmed(confirmed, when);
            case OrderCancelled cancelled -> onCancelled(cancelled, when);
        }
    }

    private void onCreated(OrderCreated e, LocalDateTime when) {
        int inserted = read.sql("""
                insert ignore into order_summary (id, customer, status, total, items_count, created_at, updated_at)
                values (:id, :customer, 'CREATED', :total, :items, :when, :when)""")
                .param("id", e.orderId().toString()).param("customer", e.customer()).param("total", e.total())
                .param("items", e.lines().stream().mapToInt(LineItem::quantity).sum()).param("when", when).update();
        if (inserted == 0) {
            return; // ya estaba proyectado: se ignora (idempotencia)
        }
        for (LineItem line : e.lines()) {
            read.sql("insert ignore into order_line_read (order_id, sku, name, quantity, unit_price) values (:id, :sku, :name, :qty, :price)")
                    .param("id", e.orderId().toString()).param("sku", line.sku()).param("name", line.name())
                    .param("qty", line.quantity()).param("price", line.unitPrice()).update();
        }
        read.sql("""
                insert into customer_stats (customer, orders_count) values (:customer, 1)
                on duplicate key update orders_count = orders_count + 1""").param("customer", e.customer()).update();
    }

    private void onConfirmed(OrderConfirmed e, LocalDateTime when) {
        int changed = read.sql("update order_summary set status = 'CONFIRMED', updated_at = :when where id = :id and status = 'CREATED'")
                .param("when", when).param("id", e.orderId().toString()).update();
        if (changed == 0) {
            return;
        }
        var summary = read.sql("select customer, total from order_summary where id = :id").param("id", e.orderId().toString())
                .query((rs, n) -> new Object[] {rs.getString("customer"), rs.getBigDecimal("total")}).single();
        read.sql("update customer_stats set confirmed_count = confirmed_count + 1, total_spent = total_spent + :total where customer = :customer")
                .param("total", (BigDecimal) summary[1]).param("customer", (String) summary[0]).update();
        var lines = read.sql("select sku, name, quantity, unit_price from order_line_read where order_id = :id").param("id", e.orderId().toString())
                .query((rs, n) -> new LineItem(rs.getString("sku"), rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
                .list();
        for (LineItem line : lines) {
            read.sql("""
                    insert into product_sales (sku, name, units_sold, revenue) values (:sku, :name, :units, :revenue)
                    on duplicate key update units_sold = units_sold + :units, revenue = revenue + :revenue, name = :name""")
                    .param("sku", line.sku()).param("name", line.name()).param("units", line.quantity()).param("revenue", line.subtotal()).update();
        }
    }

    private void onCancelled(OrderCancelled e, LocalDateTime when) {
        int changed = read.sql("update order_summary set status = 'CANCELLED', updated_at = :when where id = :id and status = 'CREATED'")
                .param("when", when).param("id", e.orderId().toString()).update();
        if (changed == 0) {
            return;
        }
        read.sql("""
                update customer_stats set cancelled_count = cancelled_count + 1
                where customer = (select customer from order_summary where id = :id)""").param("id", e.orderId().toString()).update();
    }
}
