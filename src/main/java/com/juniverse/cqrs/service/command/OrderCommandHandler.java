package com.juniverse.cqrs.service.command;

import com.juniverse.cqrs.repository.EventStore;
import com.juniverse.cqrs.model.event.LineItem;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.juniverse.cqrs.exception.OrderNotFoundException;
import com.juniverse.cqrs.model.Order;
import com.juniverse.cqrs.repository.OrderWriteRepository;

/**
 * Lado de <b>comandos</b>: ejecuta las intenciones del usuario ("crear pedido", "confirmar", "cancelar"). Cada comando, en UNA
 * transacción de PostgreSQL, cambia el modelo y registra el evento: o se guardan los dos o ninguno. Devuelve solo el
 * identificador y el número de evento, nunca vistas de datos (eso es trabajo del lado de consultas).
 */
@Service
public class OrderCommandHandler {

    /**
     * Resultado de un comando.
     *
     * @param orderId pedido afectado
     * @param eventSeq número de secuencia del evento generado
     */
    public record CommandResult(UUID orderId, long eventSeq) {
    }

    private final OrderWriteRepository repository;
    private final EventStore events;

    /**
     * Crea el manejador.
     *
     * @param repository modelo de escritura
     * @param events registro de eventos
     */
    public OrderCommandHandler(OrderWriteRepository repository, EventStore events) {
        this.repository = repository;
        this.events = events;
    }

    /**
     * Comando: crear un pedido.
     *
     * @param customer cliente
     * @param lines líneas
     * @return id del pedido y número del evento
     */
    @Transactional("writeTx")
    public CommandResult create(String customer, List<LineItem> lines) {
        Order order = Order.create(customer, lines);
        repository.insert(order);
        long seq = events.append(order.createdEvent());
        return new CommandResult(order.getId(), seq);
    }

    /**
     * Comando: confirmar un pedido.
     *
     * @param id pedido
     * @return id del pedido y número del evento
     */
    @Transactional("writeTx")
    public CommandResult confirm(UUID id) {
        Order order = repository.find(id).orElseThrow(() -> new OrderNotFoundException(id));
        var event = order.confirm();
        repository.updateStatus(order);
        return new CommandResult(id, events.append(event));
    }

    /**
     * Comando: cancelar un pedido.
     *
     * @param id pedido
     * @param reason motivo opcional
     * @return id del pedido y número del evento
     */
    @Transactional("writeTx")
    public CommandResult cancel(UUID id, String reason) {
        Order order = repository.find(id).orElseThrow(() -> new OrderNotFoundException(id));
        var event = order.cancel(reason);
        repository.updateStatus(order);
        return new CommandResult(id, events.append(event));
    }
}
