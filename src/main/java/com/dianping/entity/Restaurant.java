package com.dianping.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Restaurant {

    private Long id;

    private String name;

    private String description;

    private String address;

    private Long canteenId;

    private String floor;

    private String windowNumber;

    private String pickupInstructions;

    private Integer status;

    private BigDecimal averageRating;

    private Long reviewCount;

    private LocalDateTime createdAt;
}
