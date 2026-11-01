package com.juniverse.cqrs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code cqrs.*}.
 *
 * @param projectionDelayMs milisegundos entre una vuelta del proyector y la siguiente
 * @param batchSize eventos que se procesan por transacción
 */
@ConfigurationProperties(prefix = "cqrs")
public record CqrsProperties(long projectionDelayMs, int batchSize) {
}
