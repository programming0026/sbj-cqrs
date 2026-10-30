package com.juniverse.cqrs.query;

import com.juniverse.cqrs.event.LineItem;
import java.util.List;

/**
 * Detalle de un pedido: el resumen con sus líneas.
 *
 * @param summary resumen
 * @param lines líneas del pedido
 */
public record OrderDetailView(OrderSummaryView summary, List<LineItem> lines) {
}
