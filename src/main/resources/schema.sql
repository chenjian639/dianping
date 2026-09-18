-- Dodo Food 建表脚本（与代码保持一致）
-- 全新库可直接执行；若已有旧库，请参考末尾的增量迁移语句

CREATE DATABASE IF NOT EXISTS dodo_food
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE dodo_food;


-- =========================
-- 1. 用户表
-- =========================
CREATE TABLE user (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                       username VARCHAR(50) NOT NULL COMMENT '用户名',
                       password VARCHAR(100) NOT NULL COMMENT '密码',
                       nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
                       phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
                       role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/MERCHANT/ADMIN',
                       restaurant_id BIGINT DEFAULT NULL COMMENT '商家所属餐厅ID，普通用户为空',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                       UNIQUE KEY uk_username (username),
                       UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- =========================
-- 2. 商家表
-- =========================
CREATE TABLE restaurant (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商家ID',
                            name VARCHAR(100) NOT NULL COMMENT '商家名称',
                            description VARCHAR(255) DEFAULT NULL COMMENT '商家简介',
                            address VARCHAR(255) DEFAULT NULL COMMENT '地址',
                            status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1营业，0休息',
                            -- 评分冗余统计字段，新增评价时同步维护，避免搜索时实时聚合
                            average_rating DECIMAL(2, 1) NOT NULL DEFAULT 0.0 COMMENT '平均评分',
                            review_count INT NOT NULL DEFAULT 0 COMMENT '评价数量',
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                            INDEX idx_restaurant_status_created
                                (status, created_at DESC, id DESC),
                            FULLTEXT INDEX ft_restaurant_name_ngram (name) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';


-- =========================
-- 3. 菜品表
-- =========================
CREATE TABLE dish (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜品ID',
                       restaurant_id BIGINT NOT NULL COMMENT '所属商家ID',
                       name VARCHAR(100) NOT NULL COMMENT '菜品名称',
                       description VARCHAR(255) DEFAULT NULL COMMENT '菜品描述',
                       price DECIMAL(10, 2) NOT NULL COMMENT '价格',
                       stock INT NOT NULL DEFAULT 0 COMMENT '库存',
                       status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
                       deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0未删除，1已删除',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                       INDEX idx_dish_restaurant_deleted_status_created
                           (restaurant_id, deleted, status, created_at DESC, id DESC),

                       CONSTRAINT fk_dish_restaurant
                           FOREIGN KEY (restaurant_id)
                               REFERENCES restaurant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';


-- =========================
-- 4. 购物车表
-- =========================
CREATE TABLE cart_item (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '购物车项ID',
                            user_id BIGINT NOT NULL COMMENT '用户ID',
                            dish_id BIGINT NOT NULL COMMENT '菜品ID',
                            quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',

                            UNIQUE KEY uk_user_dish (user_id, dish_id),

                            INDEX idx_user_id (user_id),
                            INDEX idx_dish_id (dish_id),

                            CONSTRAINT fk_cart_user
                                FOREIGN KEY (user_id)
                                    REFERENCES user(id),

                            CONSTRAINT fk_cart_dish
                                FOREIGN KEY (dish_id)
                                    REFERENCES dish(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';


-- =========================
-- 5. 订单表
-- =========================
CREATE TABLE orders (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
                         user_id BIGINT NOT NULL COMMENT '用户ID',
                         restaurant_id BIGINT NOT NULL COMMENT '商家ID',
                         total_amount DECIMAL(10, 2) NOT NULL COMMENT '订单总金额',
                         status TINYINT NOT NULL DEFAULT 0
                             COMMENT '订单状态：0待支付，1已支付，2制作中，3配送中，4已完成，5已取消',
                         -- 客户端幂等键，与 user_id 组成唯一约束，防重复下单
                         request_id VARCHAR(64) NOT NULL COMMENT '客户端幂等键',
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                         UNIQUE KEY uk_user_request (user_id, request_id),
                         INDEX idx_orders_user_created
                             (user_id, created_at DESC, id DESC),
                         INDEX idx_orders_restaurant_status_created
                             (restaurant_id, status, created_at DESC, id DESC),

                         CONSTRAINT fk_order_user
                             FOREIGN KEY (user_id)
                                 REFERENCES user(id),

                         CONSTRAINT fk_order_restaurant
                             FOREIGN KEY (restaurant_id)
                                 REFERENCES restaurant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


-- =========================
-- 6. 订单明细表
-- =========================
CREATE TABLE order_item (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细ID',
                             order_id BIGINT NOT NULL COMMENT '订单ID',
                             dish_id BIGINT NOT NULL COMMENT '菜品ID',
                             dish_name VARCHAR(100) NOT NULL COMMENT '下单时菜品名称',
                             price DECIMAL(10, 2) NOT NULL COMMENT '下单时菜品单价',
                             quantity INT NOT NULL COMMENT '购买数量',

                             INDEX idx_order_id (order_id),
                             INDEX idx_dish_id (dish_id),

                             CONSTRAINT fk_order_item_order
                                 FOREIGN KEY (order_id)
                                     REFERENCES orders(id),

                             CONSTRAINT fk_order_item_dish
                                 FOREIGN KEY (dish_id)
                                     REFERENCES dish(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';


-- =========================
-- 7. 用户评价表
-- =========================
CREATE TABLE review (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
                         user_id BIGINT NOT NULL COMMENT '用户ID',
                         restaurant_id BIGINT NOT NULL COMMENT '餐厅ID',
                         order_id BIGINT NOT NULL COMMENT '订单ID',
                         rating TINYINT NOT NULL COMMENT '评分：1-5',
                         content VARCHAR(1000) DEFAULT NULL COMMENT '评价内容',
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                         UNIQUE KEY uk_order_id (order_id),

                         INDEX idx_review_user_created
                             (user_id, created_at DESC),
                         INDEX idx_review_restaurant_created
                             (restaurant_id, created_at DESC),
                         INDEX idx_review_restaurant_rating
                             (restaurant_id, rating),

                         CONSTRAINT fk_review_user
                             FOREIGN KEY (user_id) REFERENCES user(id),

                         CONSTRAINT fk_review_restaurant
                             FOREIGN KEY (restaurant_id) REFERENCES restaurant(id),

                         CONSTRAINT fk_review_order
                             FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
    COMMENT='用户评价表';


-- =========================
-- 8. Outbox 事件表（可靠投递）
-- =========================
CREATE TABLE outbox_event (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
                              event_id VARCHAR(64) NOT NULL COMMENT '业务事件ID（幂等）',
                              event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
                              aggregate_id BIGINT DEFAULT NULL COMMENT '聚合根ID',
                              payload TEXT COMMENT '事件负载',
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                  COMMENT '状态：PENDING/PROCESSING/SENT/FAILED',
                              retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
                              processing_at DATETIME DEFAULT NULL COMMENT '开始处理时间，用于卡死恢复',
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                              UNIQUE KEY uk_event_id (event_id),
                              INDEX idx_outbox_status_id (status, id),
                              INDEX idx_outbox_processing_at (processing_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox事件表';


-- =========================
-- 增量迁移（已有旧库时执行）
-- =========================
-- ALTER TABLE restaurant
--     ADD COLUMN average_rating DECIMAL(2,1) NOT NULL DEFAULT 0.0 COMMENT '平均评分',
--     ADD COLUMN review_count INT NOT NULL DEFAULT 0 COMMENT '评价数量';
--
-- ALTER TABLE orders
--     ADD COLUMN request_id VARCHAR(64) NOT NULL COMMENT '客户端幂等键',
--     ADD UNIQUE KEY uk_user_request (user_id, request_id);
