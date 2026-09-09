package com.dianping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDishStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}