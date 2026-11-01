package com.juniverse.cqrs.exception;

/** Otro comando modificó el pedido a la vez (409): el cliente debe reintentar. */
public class ConcurrentModificationConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id pedido en conflicto
     */
    public ConcurrentModificationConflictException(java.util.UUID id) {
        super("El pedido " + id + " fue modificado por otra operación; vuelve a intentarlo");
    }
}
