package com.juniverse.cqrs.model.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento leído del registro.
 *
 * @param seq número de secuencia global (orden de los eventos)
 * @param eventId identificador único del evento
 * @param event el evento
 * @param occurredAt cuándo ocurrió
 */
public record StoredEvent(long seq, UUID eventId, DomainEvent event, OffsetDateTime occurredAt) {
}
