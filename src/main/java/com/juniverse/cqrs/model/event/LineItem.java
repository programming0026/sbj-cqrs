package com.juniverse.cqrs.model.event;

import java.math.BigDecimal;

/**
 * Línea de un pedido.
 *
 * @param sku código del producto
 * @param name nombre del producto
 * @param quantity cantidad
 * @param unitPrice precio unitario
 */
public record LineItem(String sku, String name, int quantity, BigDecimal unitPrice) {

    /**
     * Importe de la línea.
     *
     * @return cantidad por precio unitario
     */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
