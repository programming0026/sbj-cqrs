package com.juniverse.cqrs.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Línea de la petición.
 *
 * @param sku código
 * @param name nombre
 * @param quantity cantidad
 * @param unitPrice precio unitario
 */
public record LineRequest(
        @NotBlank(message = "El SKU es obligatorio") @Size(max = 40) String sku,
        @NotBlank(message = "El nombre del producto es obligatorio") @Size(max = 100) String name,
        @Min(value = 1, message = "La cantidad mínima es 1") int quantity,
        @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero") BigDecimal unitPrice) {
}
