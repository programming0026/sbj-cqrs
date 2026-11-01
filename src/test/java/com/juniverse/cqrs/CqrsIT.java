package com.juniverse.cqrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juniverse.cqrs.service.command.OrderCommandHandler;
import com.juniverse.cqrs.repository.OrderWriteRepository;
import com.juniverse.cqrs.model.event.LineItem;
import com.juniverse.cqrs.service.projection.OrderProjector;
import com.juniverse.cqrs.service.projection.ProjectionRunner;
import com.juniverse.cqrs.service.query.OrderQueryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * CQRS de extremo a extremo con PostgreSQL (escritura) y MySQL (lectura) reales vía Testcontainers (requiere Docker).
 */
@SpringBootTest(properties = "cqrs.projection-delay-ms=150")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("CQRS: comandos en PostgreSQL, consultas en MySQL")
class CqrsIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.write.jdbc-url", postgres::getJdbcUrl);
        registry.add("app.datasource.write.username", postgres::getUsername);
        registry.add("app.datasource.write.password", postgres::getPassword);
        registry.add("app.datasource.read.jdbc-url", () -> mysql.getJdbcUrl() + "?connectionTimeZone=UTC");
        registry.add("app.datasource.read.username", mysql::getUsername);
        registry.add("app.datasource.read.password", mysql::getPassword);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private OrderCommandHandler commands;
    @Autowired private OrderQueryService queries;
    @Autowired private OrderWriteRepository writeRepository;
    @Autowired private OrderProjector projector;
    @Autowired private ProjectionRunner runner;
    @Autowired @Qualifier("writeJdbc") private JdbcClient writeJdbc;
    @Autowired @Qualifier("readJdbc") private JdbcClient readJdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        runner.resume();
    }

    @AfterEach
    void resume() {
        runner.resume();
    }

    private static List<LineItem> lines(String sku, int qty, String price) {
        return List.of(new LineItem(sku, "Producto " + sku, qty, new BigDecimal(price)));
    }

    private void awaitProjected(UUID id) {
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> queries.findSummary(id.toString()).isPresent());
    }

    private void awaitStatus(UUID id, String status) {
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> queries.findSummary(id.toString()).map(s -> s.status().equals(status)).orElse(false));
    }

    private void awaitCaughtUp() {
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            long written = writeJdbc.sql("select coalesce(max(seq), 0) from domain_events").query(Long.class).single();
            return projector.projectedSeq() >= written;
        });
    }

    @Test
    @DisplayName("un comando escribe en PostgreSQL y la consulta lo lee de MySQL")
    void commandThenQuery() {
        var result = commands.create("ana", lines("A1", 2, "10.50"));

        assertEquals(1, writeJdbc.sql("select count(*) from orders where id = :id").param("id", result.orderId()).query(Long.class).single());
        awaitProjected(result.orderId());

        var detail = queries.findDetail(result.orderId().toString()).orElseThrow();
        assertEquals("CREATED", detail.summary().status());
        assertEquals(0, new BigDecimal("21.00").compareTo(detail.summary().total()));
        assertEquals(2, detail.summary().itemsCount());
        assertEquals("A1", detail.lines().get(0).sku());
    }

    @Test
    @DisplayName("las dos bases son distintas: cada tabla existe solo en su lado")
    void separateStores() {
        assertEquals(0, readJdbc.sql("select count(*) from information_schema.tables where table_schema = database() and table_name in ('orders', 'domain_events')")
                .query(Long.class).single(), "MySQL no tiene el modelo de escritura");
        assertEquals(0, writeJdbc.sql("select count(*) from information_schema.tables where table_name in ('order_summary', 'customer_stats', 'product_sales')")
                .query(Long.class).single(), "PostgreSQL no tiene las vistas de lectura");
    }

    @Test
    @DisplayName("ciclo de vida: confirmar actualiza el resumen, las estadísticas del cliente y las ventas por producto")
    void confirmUpdatesReadModels() {
        String customer = "cliente-" + UUID.randomUUID();
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        var order = commands.create(customer, List.of(new LineItem(sku, "Teclado", 3, new BigDecimal("100.00"))));
        awaitProjected(order.orderId());
        commands.confirm(order.orderId());
        awaitStatus(order.orderId(), "CONFIRMED");

        var stats = queries.customerStats(customer).orElseThrow();
        assertEquals(1, stats.ordersCount());
        assertEquals(1, stats.confirmedCount());
        assertEquals(0, new BigDecimal("300.00").compareTo(stats.totalSpent()));

        var top = queries.topProducts(50).stream().filter(p -> p.sku().equals(sku)).findFirst().orElseThrow();
        assertEquals(3, top.unitsSold());
        assertEquals(0, new BigDecimal("300.00").compareTo(top.revenue()));
    }

    @Test
    @DisplayName("cancelar actualiza el estado y el contador de cancelados; no suma al gasto")
    void cancelUpdatesReadModels() {
        String customer = "cliente-" + UUID.randomUUID();
        var order = commands.create(customer, lines("C1", 1, "50.00"));
        commands.cancel(order.orderId(), "ya no lo quiero");
        awaitStatus(order.orderId(), "CANCELLED");

        var stats = queries.customerStats(customer).orElseThrow();
        assertEquals(1, stats.cancelledCount());
        assertEquals(0, stats.confirmedCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.totalSpent()));
    }

    @Test
    @DisplayName("las reglas del dominio viven en el lado de comandos: 409, 400 y 404")
    void domainRules() throws Exception {
        var order = commands.create("regla-" + UUID.randomUUID(), lines("R1", 1, "5.00"));
        commands.confirm(order.orderId());

        mvc.perform(post("/api/commands/orders/" + order.orderId() + "/confirm")).andExpect(status().isConflict());
        mvc.perform(post("/api/commands/orders/" + order.orderId() + "/cancel")).andExpect(status().isConflict());
        mvc.perform(post("/api/commands/orders/" + UUID.randomUUID() + "/confirm")).andExpect(status().isNotFound());
        mvc.perform(post("/api/commands/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer\":\"x\",\"lines\":[{\"sku\":\"A\",\"name\":\"a\",\"quantity\":1,\"unitPrice\":1},{\"sku\":\"A\",\"name\":\"a\",\"quantity\":1,\"unitPrice\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("El SKU A está repetido en el pedido"));
        mvc.perform(post("/api/commands/orders").contentType(MediaType.APPLICATION_JSON).content("{\"customer\":\"\",\"lines\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("API: el comando responde 202 con la URL de consulta y la consulta ve el pedido")
    void api() throws Exception {
        String customer = "api-" + UUID.randomUUID();
        String body = mvc.perform(post("/api/commands/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer\":\"" + customer + "\",\"lines\":[{\"sku\":\"X1\",\"name\":\"Mouse\",\"quantity\":2,\"unitPrice\":20.00}]}"))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"orderId\":\"([0-9a-f-]{36})\".*", "$1");
        awaitProjected(UUID.fromString(id));

        mvc.perform(get("/api/queries/orders/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.customer").value(customer)).andExpect(jsonPath("$.lines[0].sku").value("X1"));
        mvc.perform(get("/api/queries/orders").param("customer", customer)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/queries/customers/" + customer + "/stats")).andExpect(jsonPath("$.ordersCount").value(1));
        mvc.perform(get("/api/queries/customers/nadie-" + UUID.randomUUID() + "/stats")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/projections/status")).andExpect(jsonPath("$.paused").value(false));
    }

    @Test
    @DisplayName("listado: filtros por cliente y estado, orden por fecha y paginación")
    void searchAndPagination() {
        String customer = "pag-" + UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(commands.create(customer, lines("P" + i, i, "10.00")).orderId());
        }
        commands.confirm(ids.get(0));
        commands.cancel(ids.get(1), null);
        awaitStatus(ids.get(0), "CONFIRMED");
        awaitStatus(ids.get(1), "CANCELLED");
        awaitProjected(ids.get(4));

        var firstPage = queries.search(customer, null, 0, 2);
        assertEquals(5, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        assertEquals(2, firstPage.content().size());
        assertEquals(ids.get(4).toString(), firstPage.content().get(0).id(), "el más reciente primero");
        assertEquals(3, queries.search(customer, "created", 0, 10).totalElements());
        assertEquals(1, queries.search(customer, "CONFIRMED", 0, 10).totalElements());
        assertEquals(0, queries.search("otro-" + UUID.randomUUID(), null, 0, 10).totalElements());
    }

    @Test
    @DisplayName("CONSISTENCIA EVENTUAL: con la proyección en pausa el comando funciona pero la consulta no lo ve; al reanudar aparece")
    void eventualConsistency() throws Exception {
        awaitCaughtUp();
        runner.pause();
        Thread.sleep(400); // por si una vuelta ya estaba en curso
        var order = commands.create("eventual-" + UUID.randomUUID(), lines("E1", 1, "9.00"));

        Thread.sleep(800);
        assertTrue(queries.findSummary(order.orderId().toString()).isEmpty(), "la vista de lectura aún no lo conoce");
        mvc.perform(get("/api/queries/orders/" + order.orderId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/projections/status")).andExpect(jsonPath("$.lag").value(1)).andExpect(jsonPath("$.paused").value(true));
        assertEquals(1, writeRepositoryHas(order.orderId()), "pero el modelo de escritura ya lo tiene");

        runner.resume();
        awaitProjected(order.orderId());
        mvc.perform(get("/api/admin/projections/status")).andExpect(jsonPath("$.lag").value(0));
    }

    private long writeRepositoryHas(UUID id) {
        return writeJdbc.sql("select count(*) from orders where id = :id").param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("las vistas son desechables: se borran y se reconstruyen desde los eventos con el mismo resultado")
    void rebuildFromEvents() {
        String customer = "rebuild-" + UUID.randomUUID();
        var a = commands.create(customer, lines("RB1", 2, "10.00"));
        var b = commands.create(customer, lines("RB2", 1, "30.00"));
        commands.confirm(a.orderId());
        commands.cancel(b.orderId(), null);
        awaitStatus(a.orderId(), "CONFIRMED");
        awaitStatus(b.orderId(), "CANCELLED");
        awaitCaughtUp();

        long ordersBefore = queries.countOrders();
        var statsBefore = queries.customerStats(customer).orElseThrow();
        var topBefore = queries.topProducts(50);

        runner.pause();
        int replayed = projector.rebuild();

        assertTrue(replayed >= 4);
        assertEquals(ordersBefore, queries.countOrders());
        assertEquals(statsBefore, queries.customerStats(customer).orElseThrow());
        assertEquals(topBefore, queries.topProducts(50));
    }

    @Test
    @DisplayName("ORDEN BAJO CONCURRENCIA: 25 comandos simultáneos, ningún evento se pierde en la proyección")
    void concurrentCommandsAreAllProjected() throws Exception {
        String customer = "conc-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            int n = i;
            futures.add(pool.submit(() -> commands.create(customer, lines("K" + n, 1, "1.00")).orderId()));
        }
        for (Future<UUID> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> queries.search(customer, null, 0, 1).totalElements() == 25);
        assertEquals(25, queries.customerStats(customer).orElseThrow().ordersCount());
        assertEquals(25, writeJdbc.sql("select count(*) from orders where customer = :c").param("c", customer).query(Long.class).single());
        assertFalse(writeRepository.count() == 0);
    }
}
