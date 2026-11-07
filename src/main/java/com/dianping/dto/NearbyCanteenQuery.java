package com.dianping.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class NearbyCanteenQuery {
    @NotNull @Pattern(regexp = "WGS84")
    private String coordinateSystem = "WGS84";
    @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 8)
    @Schema(description = "本次参考位置WGS84经度；不保存用户位置")
    private BigDecimal longitude;
    @NotNull @DecimalMin("-85.05112878") @DecimalMax("85.05112878") @Digits(integer = 2, fraction = 8)
    private BigDecimal latitude;
    @NotNull @Min(1) @Max(10000)
    private Integer radiusMeters = 1000;
    @NotNull @Min(1) @Max(100)
    private Integer limit = 20;
}
