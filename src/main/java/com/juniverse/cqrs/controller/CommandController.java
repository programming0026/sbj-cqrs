package com.juniverse.cqrs.controller;

import com.juniverse.cqrs.dto.CancelRequest;
import com.juniverse.cqrs.dto.CreateOrderRequest;
import com.juniverse.cqrs.model.event.LineItem;
import jakarta.validation.Valid;
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
