package com.juniverse.cqrs.dto;

import java.math.BigDecimal;

/**
 * Estadísticas de un cliente (vista precalculada: no se suma al consultar).
 *
 * @param customer cliente
 * @param ordersCount pedidos creados
 * @param confirmedCount pedidos confirmados
 * @param cancelledCount pedidos cancelados
 * @param totalSpent gasto en pedidos confirmados
 */
public record CustomerStatsView(String customer, int ordersCount, int confirmedCount, int cancelledCount, BigDecimal totalSpent) {
}
