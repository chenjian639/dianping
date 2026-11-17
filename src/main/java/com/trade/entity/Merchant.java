package com.trade.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Merchant {

    private Long id;

    private String name;

    private String description;

    private String address;





    private Integer status;

    private BigDecimal averageRating;

    private Long reviewCount;

    private LocalDateTime createdAt;
}
