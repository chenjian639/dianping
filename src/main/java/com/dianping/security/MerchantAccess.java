package com.dianping.security;

import com.dianping.exception.BusinessException;

/** Role authorization is in SecurityConfig; this checks the merchant's resource binding. */
public final class MerchantAccess {
    private MerchantAccess() {}
    public static void requireRestaurant(Long restaurantId) {
        if (restaurantId == null) throw new BusinessException(403, "商家未绑定档口");
    }
}
