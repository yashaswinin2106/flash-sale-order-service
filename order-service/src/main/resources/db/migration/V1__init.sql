CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            TEXT        NOT NULL,
    price           NUMERIC(10,2) NOT NULL,
    total_stock     INT         NOT NULL,
    available_stock INT         NOT NULL CHECK (available_stock >= 0),
    version         INT         NOT NULL DEFAULT 0,
    sale_starts_at  TIMESTAMPTZ NOT NULL,
    sale_ends_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         TEXT        NOT NULL,
    product_id      BIGINT      NOT NULL REFERENCES products(id),
    quantity        INT         NOT NULL CHECK (quantity > 0),
    amount          NUMERIC(10,2) NOT NULL,
    status          TEXT        NOT NULL,   -- PENDING_PAYMENT, CONFIRMED, FAILED
    idempotency_key TEXT        NOT NULL,
    reservation_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE TABLE payments (
    id          UUID PRIMARY KEY,
    order_id    UUID        NOT NULL UNIQUE REFERENCES orders(id),
    status      TEXT        NOT NULL,       -- SUCCEEDED, DECLINED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One product with 100 units. The sale window is wide open so later layers
-- never hit "sale not open" by accident.
INSERT INTO products (name, price, total_stock, available_stock, sale_starts_at, sale_ends_at)
VALUES ('Limited Edition Sneaker', 99.99, 100, 100, '2020-01-01T00:00:00Z', '2099-12-31T23:59:59Z');
