package com.dianping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRestaurantDTO {

    @NotBlank(message = "商家名称不能为空")
    @Size(max = 100, message = "商家名称不能超过100个字符")
    private String name;

    @Size(max = 255, message = "商家简介不能超过255个字符")
    private String description;

    @Size(max = 255, message = "商家地址不能超过255个字符")
    private String address;
}