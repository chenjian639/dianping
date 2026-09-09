package com.dianping.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVO {

    private Long id;

    private Long dishId;

    private String dishName;

    private BigDecimal price;

    private Integer quantity;
}