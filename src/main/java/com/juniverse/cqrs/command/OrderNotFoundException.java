package com.juniverse.cqrs.command;

/** No existe el pedido en el modelo de escritura (404). */
public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id pedido buscado
     */
    public OrderNotFoundException(java.util.UUID id) {
        super("No existe el pedido " + id);
    }
}
