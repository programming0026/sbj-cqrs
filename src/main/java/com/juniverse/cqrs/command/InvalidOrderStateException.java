package com.juniverse.cqrs.command;

/** El pedido no está en un estado que permita la operación (409). */
public class InvalidOrderStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param message motivo
     */
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
