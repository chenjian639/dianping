package com.trade.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {

    private Long id;
    private LocalDateTime expiresAt;

    private Long userId;

    private Long merchantId;

    private BigDecimal totalAmount;
    private BigDecimal goodsAmount;
    private BigDecimal freightAmount;
    private String shippingAddress;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String requestId;
}
