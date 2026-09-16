package com.dianping.service;

import com.dianping.common.PageResult;
import com.dianping.entity.*;
import com.dianping.enums.OrderStatus;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.*;
import com.dianping.vo.CartItemVO;
import com.dianping.vo.OrderDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
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

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    private void createOrderTimeoutEvent(
            Long orderId) {

        OutboxEvent event = new OutboxEvent();

        event.setEventId(
                UUID.randomUUID().toString()
        );

        event.setEventType(
                "ORDER_TIMEOUT_CHECK"
        );

        event.setAggregateId(orderId);

        event.setPayload(
                String.valueOf(orderId)
        );

        event.setStatus("PENDING");
        event.setRetryCount(0);

        outboxEventMapper.insert(event);
    }

    @Transactional
    public Long createOrder(
            Long userId,
            String requestId) {

        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(
                    "requestId不能为空"
            );
        }

        // 1. 已经成功创建过，直接返回
        Order existing =
                orderMapper.selectByUserIdAndRequestId(
                        userId,
                        requestId
                );

        if (existing != null) {
            return existing.getId();
        }

        // 2. 查询购物车
        List<CartItemVO> cartItems =
                cartItemMapper.selectCartByUserId(userId);

        if (cartItems.isEmpty()) {
            throw new BusinessException("购物车为空");
        }

        // 3. 固定库存锁顺序
        cartItems.sort(
                Comparator.comparing(
                        CartItemVO::getDishId
                )
        );

        BigDecimal totalAmount =
                BigDecimal.ZERO;

        Long restaurantId = null;

        Map<Long, Dish> dishesById =
                new HashMap<>();

        // 4. 校验并生成下单快照
        for (CartItemVO cartItem : cartItems) {

            Dish dish =
                    dishMapper.selectById(
                            cartItem.getDishId()
                    );

            if (dish == null ||
                    dish.getStatus() != 1) {

                throw new BusinessException(
                        "存在无效或下架菜品"
                );
            }

            dishesById.put(
                    dish.getId(),
                    dish
            );

            if (restaurantId == null) {

                restaurantId =
                        dish.getRestaurantId();

            } else if (!restaurantId.equals(
                    dish.getRestaurantId())) {

                throw new BusinessException(
                        "一次订单只能购买同一家商家的菜品"
                );
            }

            totalAmount =
                    totalAmount.add(
                            dish.getPrice().multiply(
                                    BigDecimal.valueOf(
                                            cartItem.getQuantity()
                                    )
                            )
                    );
        }

        // 5. 先写订单，抢占幂等键
        Order order = new Order();

        order.setUserId(userId);
        order.setRestaurantId(restaurantId);
        order.setTotalAmount(totalAmount);
        order.setStatus(
                OrderStatus.PENDING_PAYMENT.getCode()
        );
        order.setRequestId(requestId);

        try {

            orderMapper.insert(order);

        } catch (DuplicateKeyException e) {

            throw new BusinessException(
                    "订单正在创建，请勿重复提交"
            );
        }

        // 6. 原子扣库存 + 创建快照
        for (CartItemVO cartItem : cartItems) {

            Dish dish =
                    dishesById.get(
                            cartItem.getDishId()
                    );

            int rows =
                    dishMapper.decreaseStock(
                            dish.getId(),
                            cartItem.getQuantity()
                    );

            if (rows == 0) {
                throw new BusinessException(
                        dish.getName() + "库存不足"
                );
            }

            OrderItem orderItem =
                    new OrderItem();

            orderItem.setOrderId(order.getId());
            orderItem.setDishId(dish.getId());
            orderItem.setDishName(dish.getName());
            orderItem.setPrice(dish.getPrice());
            orderItem.setQuantity(
                    cartItem.getQuantity()
            );

            orderItemMapper.insert(orderItem);
        }

        // 7. 清购物车
        cartItemMapper.deleteByUserId(userId);
        createOrderTimeoutEvent(
                order.getId()
        );

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

        int rows = orderMapper.payOrder(
                orderId,
                userId,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID.getCode()
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法支付");
        }

        log.info(
                "订单支付成功, orderId={}, userId={}",
                orderId,
                userId
        );
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {

        int rows = orderMapper.cancelOrder(
                orderId,
                userId,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.CANCELED.getCode()
        );

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
        log.info(
                "订单取消成功, orderId={}, userId={}",
                orderId,
                userId
        );
    }

    public void startPreparing(Long orderId,Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                OrderStatus.PAID.getCode(),
                OrderStatus.PREPARING.getCode()
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法开始制作");
        }
    }

    public void startDelivery(Long orderId,Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                OrderStatus.PREPARING.getCode(),
                OrderStatus.DELIVERING.getCode()
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法配送");
        }
    }

    public void completeOrder(Long orderId, Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                OrderStatus.DELIVERING.getCode(),
                OrderStatus.COMPLETED.getCode()
        );

        if (rows == 0) {
            log.warn(
                    "订单支付失败, orderId={}, 原因=订单不存在或状态不允许",
                    orderId
            );
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

    @Transactional
    public void timeoutCancelOrder(Long orderId) {

        int rows =
                orderMapper.timeoutCancelOrder(
                        orderId,
                        OrderStatus.PENDING_PAYMENT.getCode(),
                        OrderStatus.CANCELED.getCode()
                );

        // 已经支付、已取消、已处理……
        // 都属于正常情况，不应该让MQ重试
        if (rows == 0) {

            log.info(
                    "订单无需超时取消，可能已支付或已取消, orderId={}",
                    orderId
            );

            return;
        }

        // 只有真正从待支付 -> 已取消的那个线程
        // 才允许恢复库存
        List<OrderItem> items =
                orderItemMapper.selectByOrderId(orderId);

        for (OrderItem item : items) {

            int affected =
                    dishMapper.increaseStock(
                            item.getDishId(),
                            item.getQuantity()
                    );

            if (affected == 0) {
                throw new BusinessException(
                        "恢复库存失败, dishId=" +
                                item.getDishId()
                );
            }
        }

        log.info(
                "订单超时取消成功并恢复库存, orderId={}",
                orderId
        );
    }

}
