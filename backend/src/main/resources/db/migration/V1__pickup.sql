CREATE TABLE pickup_orders (
    order_no          VARCHAR(20) PRIMARY KEY,
    store_id          VARCHAR(10) NOT NULL,
    customer_name     VARCHAR(80) NOT NULL,
    customer_phone    VARCHAR(20),
    status            VARCHAR(24) NOT NULL,
    pickup_code_hash  CHAR(64)    NOT NULL,
    code_attempts     INT         NOT NULL DEFAULT 0,
    assigned_to       VARCHAR(60),
    staging_location  VARCHAR(20),
    arrival_spot      VARCHAR(20),
    placed_at         TIMESTAMP   NOT NULL,
    ready_by          TIMESTAMP   NOT NULL,
    ready_at          TIMESTAMP   NULL,
    arrived_at        TIMESTAMP   NULL,
    picked_up_at      TIMESTAMP   NULL,
    hold_until        TIMESTAMP   NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_orders_board ON pickup_orders (store_id, status, ready_by);
CREATE INDEX ix_orders_hold ON pickup_orders (status, hold_until);

CREATE TABLE pickup_lines (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no           VARCHAR(20)  NOT NULL REFERENCES pickup_orders (order_no),
    sku                VARCHAR(40)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    qty                INT          NOT NULL,
    aisle              VARCHAR(10),
    picked_qty         INT,
    substitution_state VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    substitute_sku     VARCHAR(40),
    substitute_name    VARCHAR(120)
);

CREATE TABLE pickup_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no    VARCHAR(20) NOT NULL REFERENCES pickup_orders (order_no),
    from_status VARCHAR(24),
    to_status   VARCHAR(24) NOT NULL,
    actor       VARCHAR(60) NOT NULL,
    note        VARCHAR(200),
    at          TIMESTAMP   NOT NULL
);
