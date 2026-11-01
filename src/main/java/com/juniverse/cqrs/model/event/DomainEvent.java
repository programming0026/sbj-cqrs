package com.juniverse.cqrs.model.event;

import java.util.UUID;

/** Hecho que ocurrió en el dominio. Los eventos son inmutables y están en pasado: "se creó", "se confirmó". */
public sealed interface DomainEvent permits OrderCreated, OrderConfirmed, OrderCancelled {

    /**
     * Pedido al que pertenece el evento.
     *
     * @return id del pedido
     */
    UUID orderId();

    /**
     * Nombre del tipo de evento (se guarda en la columna {@code event_type}).
     *
     * @return tipo
     */
    default String type() {
        return getClass().getSimpleName();
    }
}
