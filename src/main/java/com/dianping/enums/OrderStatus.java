package com.dianping.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    PAID(1, "已支付"),
    PREPARING(2, "制作中"),
    READY_FOR_PICKUP(3, "待取餐"),
    COMPLETED(4, "已完成"),
    CANCELED(5, "已取消");

    private final Integer code;
    private final String description;

    OrderStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}