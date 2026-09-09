package com.dianping.service;

import com.dianping.dto.AddCartItemDTO;
import com.dianping.entity.CartItem;
import com.dianping.entity.Dish;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.CartItemMapper;
import com.dianping.mapper.DishMapper;
import com.dianping.vo.CartItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private DishMapper dishMapper;

    public void addItem(Long userId, AddCartItemDTO dto) {

        Dish dish = dishMapper.selectById(dto.getDishId());

        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }

        if (dish.getStatus() != 1) {
            throw new BusinessException("菜品已下架");
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

            cartItem.setQuantity(newQuantity);

            cartItemMapper.updateQuantity(cartItem);
        }
    }
    public List<CartItemVO> getCart(Long userId) {
        return cartItemMapper.selectCartByUserId(userId);
    }

    public void updateQuantity(
            Long userId,
            Long cartItemId,
            Integer quantity) {

        int rows = cartItemMapper.updateQuantityById(
                cartItemId,
                userId,
                quantity
        );

        if (rows == 0) {
            throw new BusinessException("购物车记录不存在");
        }
    }

    public void deleteItem(Long userId, Long cartItemId) {

        int rows = cartItemMapper.deleteById(
                cartItemId,
                userId
        );

        if (rows == 0) {
            throw new BusinessException("购物车记录不存在");
        }
    }
}