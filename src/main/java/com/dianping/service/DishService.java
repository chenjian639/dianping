package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.dto.CreateDishDTO;
import com.dianping.dto.UpdateDishDTO;
import com.dianping.entity.Dish;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.DishMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishService {

    @Autowired
    private DishMapper dishMapper;

    public List<Dish> findByRestaurantId(Long restaurantId) {
        return dishMapper.selectByRestaurantId(restaurantId);
    }

    public Long createDish(
            Long restaurantId,
            CreateDishDTO dto) {

        Dish dish = new Dish();

        dish.setRestaurantId(restaurantId);
        dish.setName(dto.getName());
        dish.setDescription(dto.getDescription());
        dish.setPrice(dto.getPrice());
        dish.setStock(dto.getStock());

        // 新菜默认上架
        dish.setStatus(1);

        dishMapper.insert(dish);

        return dish.getId();
    }

    public void updateDish(
            Long restaurantId,
            Long dishId,
            UpdateDishDTO dto) {

        int rows = dishMapper.updateDish(
                dishId,
                restaurantId,
                dto.getName(),
                dto.getDescription(),
                dto.getPrice(),
                dto.getStock()
        );

        if (rows == 0) {
            throw new BusinessException("菜品不存在或无权限修改");
        }
    }

    public void updateDishStatus(
            Long restaurantId,
            Long dishId,
            Integer status) {

        if (status != 0 && status != 1) {
            throw new BusinessException("菜品状态只能是0或1");
        }

        int rows = dishMapper.updateStatus(
                dishId,
                restaurantId,
                status
        );

        if (rows == 0) {
            throw new BusinessException("菜品不存在或无权限操作");
        }
    }

    public void deleteDish(
            Long restaurantId,
            Long dishId) {

        int rows = dishMapper.deleteDish(
                dishId,
                restaurantId
        );

        if (rows == 0) {
            throw new BusinessException("菜品不存在或无权限删除");
        }
    }

    public PageResult<Dish> getMerchantDishes(
            Long restaurantId,
            Integer page,
            Integer pageSize,
            Integer status) {

        if (page < 1) {
            throw new BusinessException("page不能小于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException("pageSize必须在1到100之间");
        }

        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("菜品状态只能是0或1");
        }

        int offset = (page - 1) * pageSize;

        List<Dish> records =
                dishMapper.selectMerchantDishesPage(
                        restaurantId,
                        status,
                        offset,
                        pageSize
                );

        Long total =
                dishMapper.countMerchantDishes(
                        restaurantId,
                        status
                );

        PageResult<Dish> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
}