package com.juniverse.cqrs.controller;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.juniverse.cqrs.dto.CustomerStatsView;
import com.juniverse.cqrs.dto.OrderDetailView;
import com.juniverse.cqrs.dto.OrderSummaryView;
import com.juniverse.cqrs.dto.PageView;
import com.juniverse.cqrs.dto.ProductSalesView;
import com.juniverse.cqrs.service.query.OrderQueryService;

/** API de <b>consultas</b> ({@code /api/queries}): solo lectura, siempre contra MySQL. */
@RestController
@RequestMapping("/api/queries")
public class QueryController {

    private final OrderQueryService service;

    /**
     * Crea el controlador.
     *
     * @param service servicio de consultas
     */
    public QueryController(OrderQueryService service) {
        this.service = service;
    }

    /**
     * Listado de pedidos con filtros y paginación.
     *
     * @param customer cliente
     * @param status estado (CREATED, CONFIRMED, CANCELLED)
     * @param page página (desde 0)
     * @param size tamaño (1 a 100)
     * @return página de resúmenes
     */
    @GetMapping("/orders")
    public PageView<OrderSummaryView> orders(@RequestParam(required = false) String customer, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.search(customer, status, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    /**
     * Detalle de un pedido. Devuelve 404 también durante el breve instante en que el pedido ya se escribió pero aún no se proyectó.
     *
     * @param id pedido
     * @return detalle
     */
    @GetMapping("/orders/{id}")
    public OrderDetailView order(@PathVariable String id) {
        return service.findDetail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "El pedido no existe o aún no se refleja en la vista de lectura (consistencia eventual)"));
    }

    /**
     * Estadísticas de un cliente.
     *
     * @param customer cliente
     * @return estadísticas
     */
    @GetMapping("/customers/{customer}/stats")
    public CustomerStatsView customer(@PathVariable String customer) {
        return service.customerStats(customer).orElseThrow(() -> new NoSuchElementException("Sin pedidos para " + customer));
    }

    /**
     * Productos más vendidos.
     *
     * @param limit cuántos devolver (1 a 50)
     * @return ranking
     */
    @GetMapping("/products/top")
    public List<ProductSalesView> topProducts(@RequestParam(defaultValue = "5") int limit) {
        return service.topProducts(Math.min(Math.max(limit, 1), 50));
    }
}
