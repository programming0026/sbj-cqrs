package com.juniverse.cqrs.dto;

import jakarta.validation.constraints.Size;

/**
 * Comando de cancelación.
 *
 * @param reason motivo opcional
 */
public record CancelRequest(@Size(max = 200) String reason) {
}
