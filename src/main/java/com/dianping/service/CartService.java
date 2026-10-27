package com.dianping.service;

import com.dianping.dto.AddCartItemDTO;
import com.dianping.dto.UpdateCartItemDTO;
import com.dianping.entity.CartItem;
import com.dianping.entity.Dish;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.CartItemMapper;
import com.dianping.mapper.DishMapper;
import com.dianping.vo.CartItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dianping.mapper.UserMapper;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class CartService {
    private final UserMapper userMapper;

    private void lockCart(Long userId) {
        if (userMapper.lockUser(userId) == null) throw new BusinessException("用户不存在");
    }

    private final CartItemMapper cartItemMapper;

    private final DishMapper dishMapper;

    @Transactional
    public void addItem(Long userId, AddCartItemDTO dto) {
        lockCart(userId);

        Dish dish = dishMapper.selectById(dto.getDishId());

        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }

        if (dish.getStatus() != 1) {
            throw new BusinessException("菜品已下架");
        }

        if (dto.getQuantity() == null || dto.getQuantity() < 1 || dto.getQuantity() > dish.getStock())
            throw new BusinessException("购物车数量超过菜品库存或不合法");

        List<Long> restaurantIds =
                cartItemMapper.selectRestaurantIdsByUserId(userId);

        if (!restaurantIds.isEmpty()) {

            Long cartRestaurantId =
                    restaurantIds.get(0);

            if (!cartRestaurantId.equals(
                    dish.getRestaurantId())) {

                throw new BusinessException(
                        "购物车中已有其他餐厅的菜品，请先清空购物车"
                );
            }
        }

        CartItem cartItem =
                cartItemMapper.selectByUserIdAndDishId(
                        userId,
                        dto.getDishId()
                );

        if (cartItem == null) {

            CartItem newItem = new CartItem();
            newItem.setUserId(userId);
            newItem.setDishId(dto.getDishId());
            newItem.setQuantity(dto.getQuantity());

            cartItemMapper.insert(newItem);

        } else {

            int newQuantity =
                    cartItem.getQuantity() + dto.getQuantity();

            if (newQuantity > dish.getStock()) {
                throw new BusinessException("购物车数量超过菜品库存");
            }
            cartItem.setQuantity(newQuantity);

            cartItemMapper.updateQuantity(cartItem.getId(),userId,newQuantity);
        }
    }
    public List<CartItemVO> getCart(Long userId) {
        return cartItemMapper.selectCartByUserId(userId);
    }

    @Transactional
    public void deleteItem(Long userId, Long cartItemId) {
        lockCart(userId);

        int rows = cartItemMapper.deleteById(
                cartItemId,
                userId
        );

        if (rows == 0) {
            throw new BusinessException("购物车记录不存在");
        }
    }

    @Transactional
    public void updateQuantity(
            Long userId,
            Long cartItemId,
            UpdateCartItemDTO dto) {
        lockCart(userId);
        if (dto.getQuantity() == null || dto.getQuantity() < 1) throw new BusinessException("数量必须大于零");

        CartItem cartItem =
                cartItemMapper.selectByIdAndUserId(
                        cartItemId,
                        userId
                );

        if (cartItem == null) {
            throw new BusinessException(
                    "购物车项不存在或无权限修改"
            );
        }

        Dish dish =
                dishMapper.selectById(
                        cartItem.getDishId()
                );

        if (dish == null) {
            throw new BusinessException(
                    "菜品不存在"
            );
        }

        if (dish.getStatus() != 1) {
            throw new BusinessException(
                    "菜品已下架"
            );
        }

        if (dto.getQuantity() > dish.getStock()) {
            throw new BusinessException(
                    "购物车数量超过菜品库存"
            );
        }

        int rows =
                cartItemMapper.updateQuantity(
                        cartItemId,
                        userId,
                        dto.getQuantity()
                );

        if (rows == 0) {
            throw new BusinessException(
                    "购物车项不存在或无权限修改"
            );
        }
    }
}
