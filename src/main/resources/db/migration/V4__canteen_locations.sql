CREATE TABLE canteen (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    latitude DECIMAL(10,8) NOT NULL,
    entrance_instructions VARCHAR(500) DEFAULT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_canteen_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_canteen_latitude CHECK (latitude BETWEEN -85.05112878 AND 85.05112878)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE restaurant
    ADD COLUMN canteen_id BIGINT NULL,
    ADD COLUMN floor VARCHAR(30) NULL,
    ADD COLUMN window_number VARCHAR(30) NULL,
    ADD COLUMN pickup_instructions VARCHAR(500) NULL,
    ADD CONSTRAINT fk_restaurant_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id);

-- Serializes small-campus index generations; no fabricated historical locations.
CREATE TABLE canteen_geo_state (
    id TINYINT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO canteen_geo_state(id, revision) VALUES (1, 0);
