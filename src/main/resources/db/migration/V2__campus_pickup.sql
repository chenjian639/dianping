CREATE TABLE pickup_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    capacity INT NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_slot_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant(id),
    CONSTRAINT ck_slot_capacity CHECK (capacity > 0 AND reserved >= 0 AND reserved <= capacity),
    CONSTRAINT ck_slot_time CHECK (ends_at > starts_at),
    UNIQUE KEY uk_slot_start (restaurant_id, starts_at),
    INDEX idx_slot_restaurant_time (restaurant_id, starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE orders
    ADD COLUMN pickup_slot_id BIGINT NULL,
    ADD COLUMN pickup_code VARCHAR(32) NULL,
    ADD COLUMN expires_at DATETIME NULL,
    ADD CONSTRAINT fk_order_slot FOREIGN KEY (pickup_slot_id) REFERENCES pickup_slot(id),
    ADD INDEX idx_orders_expiry (status, expires_at, id);
UPDATE orders SET expires_at = DATE_ADD(created_at, INTERVAL 30 MINUTE);
ALTER TABLE orders MODIFY status TINYINT NOT NULL DEFAULT 0
    COMMENT '0待支付,1已支付,2制作中,3待取餐(旧订单为配送中),4已完成,5已取消';
