-- ============================================================================
-- V1: Initial schema for the Canals order management service.
--
-- Design notes:
--   * UUID primary keys: avoids exposing sequential IDs externally and
--     enables client-side ID generation if ever needed.
--   * Money: NUMERIC(12,2) — never floats for currency.
--   * Geo: lat/lng as NUMERIC(9,6)/NUMERIC(10,6); precision sufficient for
--     ~10cm at the equator. PostGIS would be ideal in prod but is overkill
--     for the assessment scope (and adds image weight).
--   * FK on cascade: customers/products/warehouses are reference data and
--     should never be deleted while orders reference them — RESTRICT.
--   * Timestamps: TIMESTAMPTZ everywhere, UTC by convention.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()

-- ----------------------------------------------------------------------------
-- Customers
-- ----------------------------------------------------------------------------
CREATE TABLE customers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(254) NOT NULL UNIQUE,
    full_name  VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Products (catalogue, independent of any warehouse)
-- ----------------------------------------------------------------------------
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku         VARCHAR(64)    NOT NULL UNIQUE,
    name        VARCHAR(200)   NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Warehouses
-- ----------------------------------------------------------------------------
CREATE TABLE warehouses (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(32)    NOT NULL UNIQUE,
    name       VARCHAR(120)   NOT NULL,
    -- Geocoded location of the warehouse itself (used for distance calc).
    latitude   NUMERIC(9, 6)  NOT NULL CHECK (latitude  BETWEEN -90  AND 90),
    longitude  NUMERIC(10, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    address    VARCHAR(300)   NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Stock per (warehouse, product). The on-hand quantity is the source of truth
-- for fulfilment decisions; we lock rows from this table when reserving.
-- ----------------------------------------------------------------------------
CREATE TABLE warehouse_stock (
    warehouse_id UUID    NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    product_id   UUID    NOT NULL REFERENCES products(id)   ON DELETE RESTRICT,
    quantity     INTEGER NOT NULL CHECK (quantity >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (warehouse_id, product_id)
);

-- Reverse lookup: "which warehouses carry product X with stock >= N?"
CREATE INDEX idx_warehouse_stock_product ON warehouse_stock(product_id) WHERE quantity > 0;

-- ----------------------------------------------------------------------------
-- Orders
-- ----------------------------------------------------------------------------
CREATE TYPE order_status AS ENUM (
    'PENDING_PAYMENT',
    'PAID',
    'PAYMENT_FAILED',
    'CANCELLED'
);

CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID         NOT NULL REFERENCES customers(id)  ON DELETE RESTRICT,
    warehouse_id      UUID         NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    status            order_status NOT NULL,

    -- Shipping address (denormalised on purpose: an order is an immutable
    -- historical record and must not change if the customer later edits
    -- their address book).
    ship_address_line VARCHAR(300)   NOT NULL,
    ship_latitude     NUMERIC(9, 6)  NOT NULL,
    ship_longitude    NUMERIC(10, 6) NOT NULL,

    total_amount      NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    currency          VARCHAR(3)     NOT NULL DEFAULT 'USD',

    -- Payment trail (no PAN stored — only the masked last 4).
    payment_id        VARCHAR(80),
    card_last4        VARCHAR(4),
    paid_at           TIMESTAMPTZ,
    failure_reason    VARCHAR(500),

    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Full listing (GET /orders) sorted by newest first.
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
-- Customer-scoped listing; composite covers both the filter and the sort.
CREATE INDEX idx_orders_customer   ON orders(customer_id, created_at DESC);
CREATE INDEX idx_orders_warehouse  ON orders(warehouse_id);
CREATE INDEX idx_orders_status     ON orders(status) WHERE status = 'PENDING_PAYMENT';

-- ----------------------------------------------------------------------------
-- Order items (line items). Snapshot the unit_price at order time so future
-- price changes on the catalogue do not retro-affect historical orders.
-- ----------------------------------------------------------------------------
CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders(id)   ON DELETE CASCADE,
    product_id   UUID           NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_name VARCHAR(200)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    UNIQUE (order_id, product_id)
);

-- (order_id, product_id) UNIQUE constraint already covers order_id lookups; no extra index needed.

-- ----------------------------------------------------------------------------
-- Idempotency keys. Stripe-style: clients send Idempotency-Key header; we
-- store the key + a fingerprint of the request body + cached response. A
-- repeat with same key + same body returns the cached response; same key +
-- different body returns 422.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    key              VARCHAR(120) PRIMARY KEY,
    request_hash     VARCHAR(64)  NOT NULL,        -- SHA-256 hex (64 chars)
    response_status  INTEGER      NOT NULL,
    response_body    TEXT         NOT NULL,
    order_id         UUID         REFERENCES orders(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);
CREATE INDEX idx_idempotency_order   ON idempotency_keys(order_id) WHERE order_id IS NOT NULL;
