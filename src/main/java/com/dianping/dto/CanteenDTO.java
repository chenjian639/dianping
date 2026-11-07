package com.dianping.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "食堂入口位置，统一使用WGS84；不得直接混入GCJ-02或BD-09坐标")
public class CanteenDTO {
    @NotNull @Pattern(regexp = "WGS84")
    @Schema(description = "固定WGS84；其他坐标系须在入库前按合法来源转换")
    private String coordinateSystem = "WGS84";
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Size(max = 255)
    private String address;
    @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 8)
    @Schema(description = "WGS84经度，范围[-180,180]")
    private BigDecimal longitude;
    @NotNull @DecimalMin("-85.05112878") @DecimalMax("85.05112878") @Digits(integer = 2, fraction = 8)
    @Schema(description = "WGS84纬度，范围兼容Redis GEO")
    private BigDecimal latitude;
    @Size(max = 500)
    private String entranceInstructions;
    @NotNull
    private Boolean enabled;
}
