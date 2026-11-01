package com.juniverse.cqrs.model.event;

import java.util.UUID;

/**
 * Se canceló un pedido.
 *
 * @param orderId pedido
 * @param reason motivo (opcional)
 */
public record OrderCancelled(UUID orderId, String reason) implements DomainEvent {
}
