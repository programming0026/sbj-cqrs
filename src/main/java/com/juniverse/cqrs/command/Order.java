package com.juniverse.cqrs.command;

import com.juniverse.cqrs.event.LineItem;
import com.juniverse.cqrs.event.OrderCancelled;
import com.juniverse.cqrs.event.OrderConfirmed;
import com.juniverse.cqrs.event.OrderCreated;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado del dominio (modelo de escritura). Contiene las REGLAS de negocio y decide qué eventos se producen; no sabe nada
 * de bases de datos ni de las vistas de lectura.
 * <ul>
 *   <li>Un pedido nuevo está CREATED.</li>
 *   <li>Solo un pedido CREATED puede confirmarse o cancelarse.</li>
 *   <li>No se repite un mismo SKU en un pedido.</li>
 * </ul>
 */
public class Order {

    private final UUID id;
    private final String customer;
    private final List<LineItem> lines;
    private final BigDecimal total;
    private OrderStatus status;
    private final int version;

    /**
     * Reconstruye un pedido existente (lo usa el repositorio).
     *
     * @param id identificador
     * @param customer cliente
     * @param lines líneas
     * @param total total
     * @param status estado actual
     * @param version versión para el control de concurrencia optimista
     */
    public Order(UUID id, String customer, List<LineItem> lines, BigDecimal total, OrderStatus status, int version) {
        this.id = id;
        this.customer = customer;
        this.lines = lines;
        this.total = total;
        this.status = status;
        this.version = version;
    }

    /**
     * Crea un pedido nuevo validando las reglas.
     *
     * @param customer cliente
     * @param lines líneas del pedido
     * @return el pedido en estado CREATED
     * @throws InvalidCommandException si el cliente o las líneas no son válidos
     */
    public static Order create(String customer, List<LineItem> lines) {
        if (customer == null || customer.isBlank()) {
            throw new InvalidCommandException("El cliente es obligatorio");
        }
        if (lines == null || lines.isEmpty()) {
            throw new InvalidCommandException("El pedido debe tener al menos una línea");
        }
        Set<String> skus = new HashSet<>();
        for (LineItem line : lines) {
            if (!skus.add(line.sku())) {
                throw new InvalidCommandException("El SKU " + line.sku() + " está repetido en el pedido");
            }
        }
        BigDecimal total = lines.stream().map(LineItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(UUID.randomUUID(), customer.trim(), List.copyOf(lines), total, OrderStatus.CREATED, 0);
    }

    /**
     * Evento que describe la creación de este pedido.
     *
     * @return evento {@link OrderCreated}
     */
    public OrderCreated createdEvent() {
        return new OrderCreated(id, customer, lines, total);
    }

    /**
     * Confirma el pedido.
     *
     * @return evento {@link OrderConfirmed}
     * @throws InvalidOrderStateException si no estaba en CREATED
     */
    public OrderConfirmed confirm() {
        requireCreated("confirmar");
        status = OrderStatus.CONFIRMED;
        return new OrderConfirmed(id);
    }

    /**
     * Cancela el pedido.
     *
     * @param reason motivo opcional
     * @return evento {@link OrderCancelled}
     * @throws InvalidOrderStateException si no estaba en CREATED
     */
    public OrderCancelled cancel(String reason) {
        requireCreated("cancelar");
        status = OrderStatus.CANCELLED;
        return new OrderCancelled(id, reason);
    }

    private void requireCreated(String action) {
        if (status != OrderStatus.CREATED) {
            throw new InvalidOrderStateException("No se puede " + action + " un pedido en estado " + status);
        }
    }

    public UUID getId() { return id; }

    public String getCustomer() { return customer; }

    public List<LineItem> getLines() { return lines; }

    public BigDecimal getTotal() { return total; }

    public OrderStatus getStatus() { return status; }

    public int getVersion() { return version; }
}
