package com.trade.service;

import com.trade.dto.AddCartItemDTO;
import com.trade.dto.UpdateCartItemDTO;
import com.trade.entity.CartItem;
import com.trade.entity.Product;
import com.trade.exception.BusinessException;
import com.trade.mapper.CartItemMapper;
import com.trade.mapper.ProductMapper;
import com.trade.vo.CartItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trade.mapper.UserMapper;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class CartService {
    private final UserMapper userMapper;

    private void lockCart(Long userId) {
        if (userMapper.lockUser(userId) == null) throw new BusinessException("用户不存在");
    }

    private final CartItemMapper cartItemMapper;

    private final ProductMapper productMapper;

    @Transactional
    public void addItem(Long userId, AddCartItemDTO dto) {
        lockCart(userId);

        Product product = productMapper.selectById(dto.getProductId());

        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        if (product.getStatus() != 1) {
            throw new BusinessException("商品已下架");
        }

        if (dto.getQuantity() == null || dto.getQuantity() < 1 || dto.getQuantity() > product.getStock())
            throw new BusinessException("购物车数量超过商品库存或不合法");

        List<Long> merchantIds =
                cartItemMapper.selectMerchantIdsByUserId(userId);

        if (!merchantIds.isEmpty()) {

            Long cartMerchantId =
                    merchantIds.get(0);

            if (!cartMerchantId.equals(
                    product.getMerchantId())) {

                throw new BusinessException(
                        "购物车中已有其他商家的商品，请先清空购物车"
                );
            }
        }

        CartItem cartItem =
                cartItemMapper.selectByUserIdAndProductId(
                        userId,
                        dto.getProductId()
                );

        if (cartItem == null) {

            CartItem newItem = new CartItem();
            newItem.setUserId(userId);
            newItem.setProductId(dto.getProductId());
            newItem.setQuantity(dto.getQuantity());

            cartItemMapper.insert(newItem);

        } else {

            int newQuantity =
                    cartItem.getQuantity() + dto.getQuantity();

            if (newQuantity < 1 || newQuantity > product.getStock()) {
                throw new BusinessException("购物车数量超过商品库存");
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

        Product product =
                productMapper.selectById(
                        cartItem.getProductId()
                );

        if (product == null) {
            throw new BusinessException(
                    "商品不存在"
            );
        }

        if (product.getStatus() != 1) {
            throw new BusinessException(
                    "商品已下架"
            );
        }

        if (dto.getQuantity() > product.getStock()) {
            throw new BusinessException(
                    "购物车数量超过商品库存"
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
