package com.juniverse.cqrs.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.juniverse.cqrs.model.event.DomainEvent;
import com.juniverse.cqrs.model.event.OrderCancelled;
import com.juniverse.cqrs.model.event.OrderConfirmed;
import com.juniverse.cqrs.model.event.OrderCreated;
import com.juniverse.cqrs.model.event.StoredEvent;

/**
 * Registro de eventos en PostgreSQL (tabla {@code domain_events}): es la fuente de verdad desde la que se construyen las
 * vistas de lectura.
 *
 * <p><b>Orden garantizado:</b> {@code seq} es una secuencia de PostgreSQL, pero un número menor puede confirmarse DESPUÉS
 * de uno mayor (dos transacciones concurrentes) y el proyector, que lee "todo lo posterior a N", se saltaría el evento
 * tardío para siempre. Para evitarlo, {@link #append} toma un bloqueo asesor a nivel de transacción: así los eventos se
 * numeran y confirman en el mismo orden. A cambio se serializan las escrituras; con mucho volumen se usaría CDC
 * (Debezium) o un broker.</p>
 */
@Component
public class EventStore {

    private static final long APPEND_LOCK = 7001L;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    /**
     * Crea el registro.
     *
     * @param jdbc cliente de la base de escritura
     * @param jsonMapper convierte los eventos a JSON y de vuelta
     */
    public EventStore(@Qualifier("writeJdbc") JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Añade un evento. Debe llamarse dentro de la transacción del comando, para que el evento y el cambio del modelo se
     * confirmen juntos.
     *
     * @param event evento
     * @return número de secuencia asignado
     */
    public long append(DomainEvent event) {
        jdbc.sql("select pg_advisory_xact_lock(:key)").param("key", APPEND_LOCK).query().listOfRows();
        return jdbc.sql("""
                insert into domain_events (event_id, aggregate_id, event_type, payload, occurred_at)
                values (:eventId, :aggregateId, :type, :payload, :occurredAt) returning seq""")
                .param("eventId", UUID.randomUUID())
                .param("aggregateId", event.orderId())
                .param("type", event.type())
                .param("payload", jsonMapper.writeValueAsString(event))
                .param("occurredAt", OffsetDateTime.now())
                .query(Long.class).single();
    }

    /**
     * Eventos posteriores a una secuencia, en orden.
     *
     * @param afterSeq último número ya procesado
     * @param limit cantidad máxima
     * @return eventos
     */
    public List<StoredEvent> readAfter(long afterSeq, int limit) {
        return jdbc.sql("select seq, event_id, event_type, payload, occurred_at from domain_events where seq > :seq order by seq limit :limit")
                .param("seq", afterSeq).param("limit", limit)
                .query((rs, n) -> new StoredEvent(rs.getLong("seq"), rs.getObject("event_id", UUID.class),
                        deserialize(rs.getString("event_type"), rs.getString("payload")), rs.getObject("occurred_at", OffsetDateTime.class)))
                .list();
    }

    /**
     * Último número de secuencia registrado.
     *
     * @return máximo {@code seq}, o 0 si no hay eventos
     */
    public long lastSeq() {
        return jdbc.sql("select coalesce(max(seq), 0) from domain_events").query(Long.class).single();
    }

    private DomainEvent deserialize(String type, String payload) {
        return switch (type) {
            case "OrderCreated" -> jsonMapper.readValue(payload, OrderCreated.class);
            case "OrderConfirmed" -> jsonMapper.readValue(payload, OrderConfirmed.class);
            case "OrderCancelled" -> jsonMapper.readValue(payload, OrderCancelled.class);
            default -> throw new IllegalStateException("Tipo de evento desconocido: " + type);
        };
    }
}
