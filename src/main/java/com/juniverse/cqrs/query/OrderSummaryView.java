package com.juniverse.cqrs.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fila del listado de pedidos (vista de lectura).
 *
 * @param id pedido
 * @param customer cliente
 * @param status estado
 * @param total importe
 * @param itemsCount unidades en total
 * @param createdAt fecha de creación (UTC)
 * @param updatedAt última actualización (UTC)
 */
public record OrderSummaryView(String id, String customer, String status, BigDecimal total, int itemsCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
