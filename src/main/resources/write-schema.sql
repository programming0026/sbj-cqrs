-- MODELO DE ESCRITURA (PostgreSQL): normalizado, protege las reglas del dominio
create table if not exists orders (
    id         uuid primary key,
    customer   varchar(100)   not null,
    status     varchar(20)    not null,
    total      numeric(12, 2) not null,
    version    int            not null default 0,
    created_at timestamptz    not null
);

create table if not exists order_lines (
    order_id   uuid           not null references orders (id),
    sku        varchar(40)    not null,
    name       varchar(100)   not null,
    quantity   int            not null,
    unit_price numeric(12, 2) not null,
    primary key (order_id, sku)
);

-- REGISTRO DE EVENTOS: fuente de verdad desde la que se construyen las vistas de lectura
create table if not exists domain_events (
    seq          bigserial primary key,
    event_id     uuid         not null unique,
    aggregate_id uuid         not null,
    event_type   varchar(50)  not null,
    payload      text         not null,
    occurred_at  timestamptz  not null
);
create index if not exists idx_domain_events_aggregate on domain_events (aggregate_id);
