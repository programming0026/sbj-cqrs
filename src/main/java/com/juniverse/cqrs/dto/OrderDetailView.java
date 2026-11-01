package com.juniverse.cqrs.dto;

import com.juniverse.cqrs.model.event.LineItem;
import java.util.List;

/**
 * Detalle de un pedido: el resumen con sus líneas.
 *
 * @param summary resumen
 * @param lines líneas del pedido
 */
public record OrderDetailView(OrderSummaryView summary, List<LineItem> lines) {
}
