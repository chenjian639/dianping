package com.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVO {

    private Long id;

    private Long productId;

    private String productName;

    private BigDecimal price;

    private Integer quantity;
}