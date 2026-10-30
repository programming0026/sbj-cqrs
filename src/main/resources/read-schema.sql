-- MODELO DE LECTURA (MySQL): desnormalizado y precalculado para responder consultas rapido
create table if not exists order_summary (
    id          char(36)       primary key,
    customer    varchar(100)   not null,
    status      varchar(20)    not null,
    total       decimal(12, 2) not null,
    items_count int            not null,
    created_at  datetime(6)    not null,
    updated_at  datetime(6)    not null,
    index idx_order_summary_customer (customer),
    index idx_order_summary_status (status),
    index idx_order_summary_created (created_at)
) engine = InnoDB;

create table if not exists order_line_read (
    order_id   char(36)       not null,
    sku        varchar(40)    not null,
    name       varchar(100)   not null,
    quantity   int            not null,
    unit_price decimal(12, 2) not null,
    primary key (order_id, sku)
) engine = InnoDB;

create table if not exists customer_stats (
    customer        varchar(100)   primary key,
    orders_count    int            not null default 0,
    confirmed_count int            not null default 0,
    cancelled_count int            not null default 0,
    total_spent     decimal(14, 2) not null default 0
) engine = InnoDB;

create table if not exists product_sales (
    sku        varchar(40)    primary key,
    name       varchar(100)   not null,
    units_sold bigint         not null default 0,
    revenue    decimal(14, 2) not null default 0
) engine = InnoDB;

create table if not exists projection_checkpoint (
    name     varchar(50) primary key,
    last_seq bigint      not null
) engine = InnoDB;
