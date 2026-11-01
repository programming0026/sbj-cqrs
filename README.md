# CQRS con Spring Boot 4: escritura en PostgreSQL, consulta en MySQL

Ejemplo con **Spring Boot 4** y **Java 21** del patrón **CQRS** (*Command Query Responsibility Segregation*): separar el modelo que **cambia** los datos del modelo que los **consulta**, cada uno con su propia base de datos.

## La idea

Un mismo modelo suele servir mal a dos necesidades opuestas: para **escribir** quieres normalización, reglas de negocio y transacciones; para **leer** quieres datos ya combinados, precalculados e indexados para tu pantalla. CQRS los separa:

```text
        COMANDOS (escritura)                          CONSULTAS (lectura)
   POST /api/commands/orders ...                  GET /api/queries/orders ...
              │                                               ▲
              ▼                                               │
   ┌─────────────────────┐   eventos    ┌──────────┐   ┌──────────────┐
   │ Modelo de dominio   │ ───────────► │ Proyector│ ► │ Vistas de    │
   │ (reglas, agregado)  │  (registro)  │ (asíncrono)  │ lectura      │
   │ PostgreSQL          │              └──────────┘   │ MySQL        │
   └─────────────────────┘                             └──────────────┘
    orders, order_lines,                                order_summary, customer_stats,
    domain_events                                       product_sales (desnormalizadas)
```

1. Un **comando** (crear, confirmar, cancelar un pedido) pasa por el agregado `Order`, que valida las reglas. En **una transacción de PostgreSQL** se guarda el cambio y se registra un **evento** (`OrderCreated`, `OrderConfirmed`, `OrderCancelled`).
2. El **proyector** lee los eventos nuevos y actualiza las **vistas de lectura** en MySQL: un listado de pedidos, estadísticas por cliente y ventas por producto, ya calculadas.
3. Las **consultas** leen solo de MySQL: sin joins ni agregaciones al vuelo.

## Consistencia eventual

Como son dos bases distintas no hay una transacción que las abarque, así que la lectura va un instante por detrás de la escritura. Por eso:

- Los comandos responden **202 Accepted** con la URL de consulta, no con el pedido.
- Consultar un pedido recién creado puede dar 404 durante unos milisegundos.
- `GET /api/admin/projections/status` muestra el **retraso** (`lag`) entre el último evento escrito y el último proyectado; con `POST /api/admin/projections/pause` puedes pausar el proyector y verlo con tus propios ojos.

## Decisiones de diseño

| Tema | Cómo se resuelve |
|------|------------------|
| **Sin doble escritura** | El comando solo escribe en PostgreSQL (cambio + evento, atómicos). MySQL se actualiza después, desde el registro de eventos; si el proyector cae, retoma desde su *checkpoint* |
| **Cada evento, una vez** | Cada tanda de eventos y el *checkpoint* (`projection_checkpoint`) se confirman en la misma transacción de MySQL; la fila del checkpoint se bloquea con `FOR UPDATE`, así dos instancias no proyectan a la vez |
| **Orden de los eventos** | `seq` es una secuencia de PostgreSQL; un número menor podría confirmarse después de uno mayor y el proyector lo saltaría para siempre. `EventStore.append` toma un bloqueo asesor (`pg_advisory_xact_lock`) para que numeración y confirmación coincidan. Cuesta serializar las escrituras; con mucho volumen se usaría CDC (Debezium) o un broker |
| **Vistas desechables** | Se pueden borrar y reconstruir leyendo de nuevo todos los eventos: `POST /api/admin/projections/rebuild` |
| **Idempotencia** | Los manejadores de eventos ignoran lo ya aplicado (`insert ignore`, `status = 'CREATED'` en el `update`) |
| **Concurrencia en comandos** | Control optimista con columna `version` (dos confirmaciones simultáneas → una gana, la otra recibe 409) |
| **Reglas en un solo lado** | Solo el agregado `Order` decide: solo un pedido CREATED se confirma o cancela; no se repite un SKU. Las consultas no tienen reglas |

## API

**Comandos** (`/api/commands/orders`, escriben en PostgreSQL):

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/commands/orders` | Crear pedido `{"customer","lines":[{"sku","name","quantity","unitPrice"}]}` → 202 |
| POST | `/api/commands/orders/{id}/confirm` | Confirmar → 202 (409 si no está CREATED) |
| POST | `/api/commands/orders/{id}/cancel` | Cancelar `{"reason"}` opcional → 202 |

**Consultas** (`/api/queries`, leen de MySQL):

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/orders?customer=&status=&page=&size=` | Listado paginado, del más reciente al más antiguo |
| GET | `/orders/{id}` | Pedido con sus líneas |
| GET | `/customers/{customer}/stats` | Pedidos, confirmados, cancelados y gasto total |
| GET | `/products/top?limit=` | Productos más vendidos (solo pedidos confirmados) |

**Operación** (`/api/admin/projections`): `GET /status`, `POST /pause`, `POST /resume`, `POST /rebuild`.

## Estructura

```text
src/main/java/com/juniverse/cqrs/
├── controller/   # CommandController, QueryController, ProjectionAdminController
├── service/
│   ├── command/      # OrderCommandHandler
│   ├── query/        # OrderQueryService
│   └── projection/   # OrderProjector, ProjectionRunner
├── repository/   # OrderWriteRepository, EventStore
├── model/        # Order (agregado), OrderStatus
│   └── event/    # DomainEvent (sealed), OrderCreated/Confirmed/Cancelled, LineItem, StoredEvent
├── dto/          # vistas de lectura (records)
├── exception/    # excepciones de dominio y ApiExceptionHandler (Problem Details)
└── config/       # dos DataSource/JdbcClient/transacciones, creación del esquema, CqrsProperties
```

Las dos bases se configuran por separado (`app.datasource.write.*` y `app.datasource.read.*`), cada una con su pool, su `JdbcClient` y su gestor de transacciones (`@Transactional("writeTx")` en comandos).

## Requisitos

- JDK 21, Maven 3.9 o superior y Docker.

## Ejecutar

```bash
docker compose up -d      # PostgreSQL 16 (puerto 5433) y MySQL 8.4 (puerto 3307)
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/commands/orders -H "Content-Type: application/json" \
     -d '{"customer":"ana","lines":[{"sku":"TEC-1","name":"Teclado","quantity":2,"unitPrice":100},{"sku":"MOU-1","name":"Mouse","quantity":1,"unitPrice":50.5}]}'
curl http://localhost:8080/api/queries/orders/<orderId>
curl -X POST http://localhost:8080/api/commands/orders/<orderId>/confirm
curl http://localhost:8080/api/queries/customers/ana/stats
curl "http://localhost:8080/api/queries/products/top?limit=3"
curl "http://localhost:8080/api/queries/orders?customer=ana&status=CONFIRMED"
```

**Experimentos:**

1. `POST /api/admin/projections/pause`, crea un pedido y consúltalo: 404; `status` muestra `lag: 1`. Con `resume` aparece.
2. Mira cada lado: `docker exec -it cqrs-write-postgres psql -U cqrs -d cqrs_write -c "select seq, event_type from domain_events"` y `docker exec -it cqrs-read-mysql mysql -ucqrs -pcqrs cqrs_read -e "select * from order_summary"`.
3. `POST /api/admin/projections/rebuild` y comprueba que las vistas quedan idénticas.

## Pruebas

```bash
mvn verify
```

10 pruebas de integración con PostgreSQL y MySQL reales (Testcontainers; requieren Docker): comando → consulta; **las tablas de cada lado no existen en el otro**; confirmar y cancelar actualizan resumen, estadísticas y ventas; reglas del dominio (409, 400, 404); API (202, `Location`); filtros y paginación; **consistencia eventual** con la proyección en pausa; **reconstrucción** de las vistas con el mismo resultado; y 25 comandos concurrentes sin perder ningún evento en la proyección.

## Cuándo usar (y cuándo no) CQRS

**Sí** cuando las lecturas y escrituras tienen necesidades muy distintas (muchas más lecturas, pantallas con datos combinados, escalar cada lado por separado) o cuando ya trabajas con eventos.

**No** en un CRUD sencillo: añade complejidad real (dos modelos, sincronización, consistencia eventual, más piezas que operar). Puedes aplicar solo la versión ligera: misma base de datos pero modelos separados para leer y escribir.

Límites de este ejemplo: el proyector sondea el registro de eventos en vez de usar un broker (Kafka) o CDC; los eventos van en JSON sin versionado de esquema; y una sola proyección por simplicidad. Puedes combinarlo con el patrón Outbox (ver `sbj-outbox-pattern`) para publicar los eventos hacia otros servicios.

Fat jar ejecutable: `mvn clean package` y `java -jar target/sbj-cqrs-0.0.1-SNAPSHOT.jar`.

## Licencia

Código de ejemplo de uso libre con fines educativos.
