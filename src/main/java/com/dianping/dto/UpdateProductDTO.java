package com.dianping.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductDTO {

    @NotBlank(message = "商品名称不能为空")
    @jakarta.validation.constraints.Size(max = 100)
    private String name;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
    private BigDecimal price;


}