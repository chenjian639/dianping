package com.dianping.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RestaurantCanteenDTO {
    @Positive
    @Schema(description = "食堂ID；null表示解除关联，仅管理员可操作")
    private Long canteenId;
}
