package com.juniverse.cqrs.controller;

import com.juniverse.cqrs.model.event.LineItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.juniverse.cqrs.service.command.OrderCommandHandler;

/**
 * API de <b>comandos</b> ({@code /api/commands}). Responde 202 Accepted: el cambio ya está registrado en el modelo de
 * escritura, pero la vista de lectura se actualizará en un instante (consistencia eventual). Por eso no devuelve el pedido
 * completo: se consulta después en {@code /api/queries/...}.
 */
@RestController
@RequestMapping("/api/commands/orders")
public class CommandController {

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

    /**
     * Comando de cancelación.
     *
     * @param reason motivo opcional
     */
    public record CancelRequest(@Size(max = 200) String reason) {
    }

    private final OrderCommandHandler handler;

    /**
     * Crea el controlador.
     *
     * @param handler manejador de comandos
     */
    public CommandController(OrderCommandHandler handler) {
        this.handler = handler;
    }

    /**
     * Crear un pedido.
     *
     * @param request datos del pedido
     * @return 202 con el id, el número de evento y la URL de consulta
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateOrderRequest request) {
        List<LineItem> lines = request.lines().stream().map(l -> new LineItem(l.sku(), l.name(), l.quantity(), l.unitPrice())).toList();
        return accepted(handler.create(request.customer(), lines));
    }

    /**
     * Confirmar un pedido.
     *
     * @param id pedido
     * @return 202
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable UUID id) {
        return accepted(handler.confirm(id));
    }

    /**
     * Cancelar un pedido.
     *
     * @param id pedido
     * @param request motivo opcional
     * @return 202
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        return accepted(handler.cancel(id, request == null ? null : request.reason()));
    }

    private static ResponseEntity<Map<String, Object>> accepted(OrderCommandHandler.CommandResult result) {
        String queryUrl = "/api/queries/orders/" + result.orderId();
        return ResponseEntity.accepted().location(URI.create(queryUrl))
                .body(Map.of("orderId", result.orderId(), "eventSeq", result.eventSeq(), "queryUrl", queryUrl));
    }
}
