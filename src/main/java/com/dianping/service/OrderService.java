package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.entity.*;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.*;
import com.dianping.vo.CartItemVO;
import com.dianping.vo.OrderDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Transactional
    public Long createOrder(Long userId) {

        List<CartItemVO> cartItems =
                cartItemMapper.selectCartByUserId(userId);

        if (cartItems.isEmpty()) {
            throw new BusinessException("购物车为空");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        Long restaurantId = null;

        for (CartItemVO cartItem : cartItems) {

            Dish dish = dishMapper.selectById(cartItem.getDishId());

            if (dish == null || dish.getStatus() != 1) {
                throw new BusinessException("存在无效或下架菜品");
            }

            if (dish.getStock() < cartItem.getQuantity()) {
                throw new BusinessException(
                        dish.getName() + "库存不足"
                );
            }

            if (restaurantId == null) {
                restaurantId = dish.getRestaurantId();
            } else if (!restaurantId.equals(dish.getRestaurantId())) {
                throw new BusinessException("一次订单只能购买同一家商家的菜品");
            }

            BigDecimal itemAmount =
                    dish.getPrice().multiply(
                            BigDecimal.valueOf(cartItem.getQuantity())
                    );

            totalAmount = totalAmount.add(itemAmount);
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setRestaurantId(restaurantId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);

        orderMapper.insert(order);

        for (CartItemVO cartItem : cartItems) {

            Dish dish = dishMapper.selectById(cartItem.getDishId());

            int rows = dishMapper.decreaseStock(
                    dish.getId(),
                    cartItem.getQuantity()
            );

            if (rows == 0) {
                throw new BusinessException(
                        dish.getName() + "库存不足"
                );
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setDishId(dish.getId());
            orderItem.setDishName(dish.getName());
            orderItem.setPrice(dish.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());

            orderItemMapper.insert(orderItem);
        }

        cartItemMapper.deleteByUserId(userId);

        return order.getId();
    }

    public PageResult<Order> getOrders(
            Long userId,
            Integer page,
            Integer pageSize) {

        int offset = (page - 1) * pageSize;

        List<Order> records =
                orderMapper.selectByUserIdPage(
                        userId,
                        offset,
                        pageSize
                );

        Long total = orderMapper.countByUserId(userId);

        PageResult<Order> result = new PageResult<>();

        result.setTotal(total);
        result.setRecords(records);

        return result;
    }
    public OrderDetailVO getOrderDetail(Long userId, Long orderId) {

        Order order =
                orderMapper.selectByIdAndUserId(orderId, userId);

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        List<OrderItem> items =
                orderItemMapper.selectByOrderId(orderId);

        OrderDetailVO vo = new OrderDetailVO();

        vo.setId(order.getId());
        vo.setRestaurantId(order.getRestaurantId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items);

        return vo;
    }

    public void payOrder(Long userId, Long orderId) {

        int rows = orderMapper.payOrder(orderId, userId);

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法支付");
        }
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {

        // 1. 先把订单从 0 改成 5
        int rows = orderMapper.cancelOrder(orderId, userId);

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法取消");
        }

        // 2. 查询这个订单买了哪些菜
        List<OrderItem> items =
                orderItemMapper.selectByOrderId(orderId);

        // 3. 把每个菜的库存加回来
        for (OrderItem item : items) {
            dishMapper.increaseStock(
                    item.getDishId(),
                    item.getQuantity()
            );
        }
    }

    public void startPreparing(Long orderId,Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                1,
                2
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法开始制作");
        }
    }

    public void startDelivery(Long orderId,Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                2,
                3
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法配送");
        }
    }

    public void completeOrder(Long orderId, Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                3,
                4
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法完成");
        }
    }

    public PageResult<Order> getMerchantOrders(
            Long restaurantId,
            Integer page,
            Integer pageSize,
            Integer status) {

        int offset = (page - 1) * pageSize;

        List<Order> records =
                orderMapper.selectMerchantOrdersPage(
                        restaurantId,
                        status,
                        offset,
                        pageSize
                );

        Long total =
                orderMapper.countMerchantOrders(
                        restaurantId,
                        status
                );

        PageResult<Order> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);

        return result;
    }

    public OrderDetailVO getMerchantOrderDetail(
            Long restaurantId,
            Long orderId) {

        Order order =
                orderMapper.selectByIdAndRestaurantId(
                        orderId,
                        restaurantId
                );

        if (order == null) {
            throw new BusinessException(
                    "订单不存在或无权限查看"
            );
        }

        List<OrderItem> items =
                orderItemMapper.selectByOrderId(orderId);

        OrderDetailVO vo = new OrderDetailVO();

        vo.setId(order.getId());
        vo.setRestaurantId(order.getRestaurantId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items);

        return vo;
    }
}