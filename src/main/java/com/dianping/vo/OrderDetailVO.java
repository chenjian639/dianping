package com.dianping.vo;

import com.dianping.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailVO {

    private Long id;
    private LocalDateTime expiresAt;

    private Long restaurantId;

    private BigDecimal totalAmount;
    private BigDecimal goodsAmount;
    private BigDecimal freightAmount;
    private String shippingAddress;

    private Integer status;

    private LocalDateTime createdAt;

    private List<OrderItem> items;
}