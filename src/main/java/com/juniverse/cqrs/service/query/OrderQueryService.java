package com.juniverse.cqrs.service.query;

import com.juniverse.cqrs.model.event.LineItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import com.juniverse.cqrs.dto.CustomerStatsView;
import com.juniverse.cqrs.dto.OrderDetailView;
import com.juniverse.cqrs.dto.OrderSummaryView;
import com.juniverse.cqrs.dto.PageView;
import com.juniverse.cqrs.dto.ProductSalesView;

/**
 * Lado de <b>consultas</b>: lee SOLO de MySQL y nunca modifica nada. Las vistas ya están desnormalizadas y precalculadas,
 * así que las consultas son simples y rápidas (sin joins ni agregaciones al vuelo).
 */
@Service
public class OrderQueryService {

    private final JdbcClient read;

    /**
     * Crea el servicio.
     *
     * @param read cliente de la base de lectura
     */
    public OrderQueryService(@Qualifier("readJdbc") JdbcClient read) {
        this.read = read;
    }

    private static OrderSummaryView toSummary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new OrderSummaryView(rs.getString("id"), rs.getString("customer"), rs.getString("status"), rs.getBigDecimal("total"),
                rs.getInt("items_count"), rs.getObject("created_at", java.time.LocalDateTime.class), rs.getObject("updated_at", java.time.LocalDateTime.class));
    }

    /**
     * Busca pedidos con filtros opcionales, del más reciente al más antiguo.
     *
     * @param customer cliente exacto, o {@code null}
     * @param status estado, o {@code null}
     * @param page página (desde 0)
     * @param size tamaño de página
     * @return página de resúmenes
     */
    public PageView<OrderSummaryView> search(String customer, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (customer != null && !customer.isBlank()) {
            where.append(" and customer = :customer");
            params.put("customer", customer);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = :status");
            params.put("status", status.toUpperCase());
        }
        long total = read.sql("select count(*) from order_summary" + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<OrderSummaryView> content = read.sql("select id, customer, status, total, items_count, created_at, updated_at from order_summary"
                + where + " order by created_at desc, id limit :limit offset :offset").params(params).query(OrderQueryService::toSummary).list();
        return new PageView<>(content, page, size, total, (int) Math.ceil(total / (double) size));
    }

    /**
     * Resumen de un pedido.
     *
     * @param id pedido
     * @return el resumen, o vacío si aún no se proyectó o no existe
     */
    public Optional<OrderSummaryView> findSummary(String id) {
        return read.sql("select id, customer, status, total, items_count, created_at, updated_at from order_summary where id = :id")
                .param("id", id).query(OrderQueryService::toSummary).optional();
    }

    /**
     * Detalle de un pedido con sus líneas.
     *
     * @param id pedido
     * @return el detalle, o vacío si aún no se proyectó o no existe
     */
    public Optional<OrderDetailView> findDetail(String id) {
        return findSummary(id).map(summary -> new OrderDetailView(summary,
                read.sql("select sku, name, quantity, unit_price from order_line_read where order_id = :id order by sku").param("id", id)
                        .query((rs, n) -> new LineItem(rs.getString("sku"), rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
                        .list()));
    }

    /**
     * Estadísticas de un cliente.
     *
     * @param customer cliente
     * @return estadísticas, o vacío si no tiene pedidos proyectados
     */
    public Optional<CustomerStatsView> customerStats(String customer) {
        return read.sql("select customer, orders_count, confirmed_count, cancelled_count, total_spent from customer_stats where customer = :customer")
                .param("customer", customer)
                .query((rs, n) -> new CustomerStatsView(rs.getString("customer"), rs.getInt("orders_count"), rs.getInt("confirmed_count"),
                        rs.getInt("cancelled_count"), rs.getBigDecimal("total_spent")))
                .optional();
    }

    /**
     * Productos más vendidos.
     *
     * @param limit cuántos devolver
     * @return ranking por unidades vendidas
     */
    public List<ProductSalesView> topProducts(int limit) {
        return read.sql("select sku, name, units_sold, revenue from product_sales order by units_sold desc, sku limit :limit").param("limit", limit)
                .query((rs, n) -> new ProductSalesView(rs.getString("sku"), rs.getString("name"), rs.getLong("units_sold"), rs.getBigDecimal("revenue")))
                .list();
    }

    /**
     * Cantidad de pedidos en la vista de lectura.
     *
     * @return filas de {@code order_summary}
     */
    public long countOrders() {
        return read.sql("select count(*) from order_summary").query(Long.class).single();
    }
}
