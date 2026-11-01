package com.juniverse.cqrs.model.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Se creó un pedido.
 *
 * @param orderId pedido
 * @param customer cliente
 * @param lines líneas
 * @param total importe total
 */
public record OrderCreated(UUID orderId, String customer, List<LineItem> lines, BigDecimal total) implements DomainEvent {
}
