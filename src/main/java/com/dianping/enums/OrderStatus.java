package com.dianping.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    PAID(1, "已支付"),
    LEGACY_PREPARING(2, "历史制作中"),
    SHIPPED(3, "已发货"),
    COMPLETED(4, "已完成"),
    CANCELED(5, "已取消"),
    REFUNDED(6, "已退款");

    private final Integer code;
    private final String description;

    OrderStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}