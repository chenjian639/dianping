-- V1-V4 remain immutable. Legacy campus records are retained, never reinterpreted as new orders.
ALTER TABLE orders ADD COLUMN shipping_address VARCHAR(500) NULL,
    ADD COLUMN goods_amount DECIMAL(10,2) NULL,
    ADD COLUMN freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
-- Stop migration if the old system still has active orders. Settle them on the old version first.
ALTER TABLE orders ADD CONSTRAINT ck_trade_legacy_closed CHECK (shipping_address IS NOT NULL OR status IN (4,5));
UPDATE orders SET goods_amount=total_amount;
ALTER TABLE orders MODIFY goods_amount DECIMAL(10,2) NOT NULL;
CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    channel_reference VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_order(order_id),
    UNIQUE KEY uk_payment_channel(channel_reference),
    FOREIGN KEY(order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE refund (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_order(order_id),
    FOREIGN KEY(order_id) REFERENCES orders(id),
    FOREIGN KEY(payment_id) REFERENCES payment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inventory_movement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    business_key VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inventory_business(product_id,reason,business_key),
    FOREIGN KEY(product_id) REFERENCES dish(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO inventory_movement(product_id,delta,reason,business_key)
    SELECT id,stock,'OPENING','V5' FROM dish;
UPDATE outbox_event SET status='SENT' WHERE event_type='CANTEEN_GEO_CHANGED' AND status<>'SENT';
