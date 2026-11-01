package com.juniverse.cqrs.repository;

import com.juniverse.cqrs.model.event.LineItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.juniverse.cqrs.exception.ConcurrentModificationConflictException;
import com.juniverse.cqrs.model.Order;
import com.juniverse.cqrs.model.OrderStatus;

/** Persistencia del modelo de escritura en PostgreSQL: tablas normalizadas {@code orders} y {@code order_lines}. */
@Repository
public class OrderWriteRepository {

    private final JdbcClient jdbc;

    /**
     * Crea el repositorio.
     *
     * @param jdbc cliente de la base de escritura
     */
    public OrderWriteRepository(@Qualifier("writeJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserta un pedido nuevo con sus líneas.
     *
     * @param order pedido
     */
    public void insert(Order order) {
        jdbc.sql("insert into orders (id, customer, status, total, version, created_at) values (:id, :customer, :status, :total, 0, :createdAt)")
                .param("id", order.getId()).param("customer", order.getCustomer()).param("status", order.getStatus().name())
                .param("total", order.getTotal()).param("createdAt", OffsetDateTime.now()).update();
        for (LineItem line : order.getLines()) {
            jdbc.sql("insert into order_lines (order_id, sku, name, quantity, unit_price) values (:orderId, :sku, :name, :quantity, :price)")
                    .param("orderId", order.getId()).param("sku", line.sku()).param("name", line.name())
                    .param("quantity", line.quantity()).param("price", line.unitPrice()).update();
        }
    }

    /**
     * Carga un pedido completo.
     *
     * @param id identificador
     * @return el pedido, si existe
     */
    public Optional<Order> find(UUID id) {
        List<LineItem> lines = jdbc.sql("select sku, name, quantity, unit_price from order_lines where order_id = :id order by sku")
                .param("id", id)
                .query((rs, n) -> new LineItem(rs.getString("sku"), rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
                .list();
        return jdbc.sql("select customer, status, total, version from orders where id = :id").param("id", id)
                .query((rs, n) -> new Order(id, rs.getString("customer"), lines, rs.getBigDecimal("total"),
                        OrderStatus.valueOf(rs.getString("status")), rs.getInt("version")))
                .optional();
    }

    /**
     * Guarda el nuevo estado con <b>bloqueo optimista</b>: solo actualiza si la versión sigue siendo la que se leyó.
     *
     * @param order pedido con el estado nuevo
     * @throws ConcurrentModificationConflictException si otro comando lo cambió mientras tanto
     */
    public void updateStatus(Order order) {
        int rows = jdbc.sql("update orders set status = :status, version = version + 1 where id = :id and version = :version")
                .param("status", order.getStatus().name()).param("id", order.getId()).param("version", order.getVersion()).update();
        if (rows == 0) {
            throw new ConcurrentModificationConflictException(order.getId());
        }
    }

    /**
     * Total de pedidos en el modelo de escritura (para pruebas y diagnóstico).
     *
     * @return cantidad
     */
    public long count() {
        return jdbc.sql("select count(*) from orders").query(Long.class).single();
    }

    /**
     * Total acumulado de todos los pedidos (para pruebas y diagnóstico).
     *
     * @return suma de los totales
     */
    public BigDecimal sumTotals() {
        return jdbc.sql("select coalesce(sum(total), 0) from orders").query(BigDecimal.class).single();
    }
}
