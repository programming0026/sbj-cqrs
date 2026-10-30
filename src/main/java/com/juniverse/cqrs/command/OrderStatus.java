package com.juniverse.cqrs.command;

/** Estados del pedido en el modelo de escritura. */
public enum OrderStatus {
    CREATED, CONFIRMED, CANCELLED
}
