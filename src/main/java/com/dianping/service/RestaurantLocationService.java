package com.dianping.service;

import com.dianping.dto.RestaurantLocationDTO;
import com.dianping.entity.Canteen;
import com.dianping.entity.Restaurant;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.CanteenMapper;
import com.dianping.mapper.RestaurantMapper;
import com.dianping.vo.RestaurantLocationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantLocationService {
    private final RestaurantMapper restaurants;
    private final CanteenMapper canteens;
    private final OutboxEventService events;

    public RestaurantLocationService(RestaurantMapper restaurants, CanteenMapper canteens,
                                     OutboxEventService events) {
        this.restaurants = restaurants;
        this.canteens = canteens;
        this.events = events;
    }

    @Transactional
    public void bindCanteen(Long restaurantId, Long canteenId) {
        canteens.lockRevision();
        if (canteenId != null && canteens.selectById(canteenId) == null) {
            throw new BusinessException(404, "食堂不存在");
        }
        requireRestaurant(restaurantId);
        restaurants.updateCanteen(restaurantId, canteenId);
        events.restaurantChanged(restaurantId);
    }

    @Transactional
    public void update(Long restaurantId, RestaurantLocationDTO dto) {
        requireRestaurant(restaurantId);
        restaurants.updateLocation(restaurantId, dto.getFloor(), dto.getWindowNumber(), dto.getPickupInstructions());
        events.restaurantChanged(restaurantId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RestaurantLocationVO get(Long restaurantId) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        Canteen canteen = restaurant.getCanteenId() == null ? null : canteens.selectById(restaurant.getCanteenId());
        return new RestaurantLocationVO(restaurant.getId(), restaurant.getCanteenId(),
                canteen == null ? null : canteen.getName(), canteen != null && canteen.getEnabled(),
                restaurant.getFloor(), restaurant.getWindowNumber(), restaurant.getPickupInstructions());
    }

    private Restaurant requireRestaurant(Long id) {
        Restaurant restaurant = restaurants.selectById(id);
        if (restaurant == null) {
            throw new BusinessException(404, "档口不存在");
        }
        return restaurant;
    }
}
