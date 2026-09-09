package com.dianping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRestaurantStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}