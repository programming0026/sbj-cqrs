package com.juniverse.cqrs.command;

/** El comando no cumple las reglas del dominio (400). */
public class InvalidCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param message motivo
     */
    public InvalidCommandException(String message) {
        super(message);
    }
}
