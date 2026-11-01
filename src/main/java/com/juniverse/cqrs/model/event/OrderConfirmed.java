package com.juniverse.cqrs.model.event;

import java.util.UUID;

/**
 * Se confirmó un pedido.
 *
 * @param orderId pedido
 */
public record OrderConfirmed(UUID orderId) implements DomainEvent {
}
