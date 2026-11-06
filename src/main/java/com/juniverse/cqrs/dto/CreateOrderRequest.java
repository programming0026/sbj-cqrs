package com.juniverse.cqrs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Comando de creación.
 *
 * @param customer cliente
 * @param lines líneas
 */
public record CreateOrderRequest(
        @NotBlank(message = "El cliente es obligatorio") @Size(max = 100) String customer,
        @NotEmpty(message = "El pedido debe tener al menos una línea") List<@Valid LineRequest> lines) {
}
