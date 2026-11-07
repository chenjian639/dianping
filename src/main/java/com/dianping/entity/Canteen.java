package com.dianping.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Canteen {
    private Long id;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String entranceInstructions;
    private Boolean enabled;
    private Boolean deleted;
}
