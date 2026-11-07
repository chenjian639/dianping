package com.dianping.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record NearbyCanteenVO(
        Long id, String name, String address, BigDecimal longitude, BigDecimal latitude,
        String entranceInstructions,
        @Schema(description = "到食堂入口的球面直线距离，单位米；不是步行距离") double distanceMeters,
        @Schema(description = "固定STRAIGHT_LINE，表示直线距离") String distanceType,
        String coordinateSystem) {
}
