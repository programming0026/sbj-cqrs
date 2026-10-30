package com.juniverse.cqrs.query;

import java.math.BigDecimal;

/**
 * Ventas de un producto (vista precalculada).
 *
 * @param sku código
 * @param name nombre
 * @param unitsSold unidades vendidas en pedidos confirmados
 * @param revenue ingresos
 */
public record ProductSalesView(String sku, String name, long unitsSold, BigDecimal revenue) {
}
