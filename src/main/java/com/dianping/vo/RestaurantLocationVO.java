package com.dianping.vo;

public record RestaurantLocationVO(
        Long restaurantId, Long canteenId, String canteenName, Boolean canteenEnabled,
        String floor, String windowNumber, String pickupInstructions) {
}
