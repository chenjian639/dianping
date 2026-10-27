package com.dianping.service;

import com.dianping.vo.OrderSummaryVO;

import com.dianping.common.PageResult;
import com.dianping.entity.*;
import com.dianping.enums.OrderStatus;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.*;
import com.dianping.vo.CartItemVO;
import com.dianping.vo.OrderDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class OrderService {

    private final CartItemMapper cartItemMapper;

    private final DishMapper dishMapper;

    private final OrderMapper orderMapper;

    private final UserMapper userMapper;

    private final PickupSlotMapper pickupSlotMapper;

    private final OrderItemMapper orderItemMapper;

    private final OutboxEventService outboxEventService;

    @Transactional
    public Long createOrder(
            Long userId,
            String requestId, Long pickupSlotId) {

        if (requestId == null || requestId.isBlank() || requestId.length() > 64 || pickupSlotId == null) {
            throw new BusinessException(
                    "requestId须为1到64字符且必须指定取餐时段"
            );
        }

        // 同一用户的下单串行化，避免不同请求重复购买同一份购物车。
        if (userMapper.lockUser(userId) == null) throw new BusinessException("用户不存在");
        // 1. 已经成功创建过，直接返回
        Order existing =
                orderMapper.selectByUserIdAndRequestId(
                        userId,
                        requestId
                );

        if (existing != null) {
            if (!Objects.equals(existing.getPickupSlotId(), pickupSlotId)) throw new BusinessException("幂等键已用于其他时段");
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
            if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1)
                throw new BusinessException("购物车数量不合法");

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

        // 先占时段，再按菜品 ID 升序扣库存；任何失败全部回滚。
        PickupSlot slot = pickupSlotMapper.selectById(pickupSlotId);
        if (slot == null || !slot.getRestaurantId().equals(restaurantId)
                || pickupSlotMapper.reserve(pickupSlotId, restaurantId) == 0) {
            throw new BusinessException("时段不属于该档口、已满或已截止预约");
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
        order.setPickupSlotId(pickupSlotId);
        order.setPickupCode(UUID.randomUUID().toString().replace("-", ""));

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
        outboxEventService.orderTimeout(order.getId());

        return order.getId();
    }

    public PageResult<OrderSummaryVO> getOrders(
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

        PageResult<OrderSummaryVO> result = new PageResult<>();

        result.setTotal(total);
        result.setRecords(records.stream().map(OrderSummaryVO::from).toList());

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
        vo.setPickupSlotId(order.getPickupSlotId());
        vo.setPickupCode(order.getPickupCode());
        vo.setExpiresAt(order.getExpiresAt());
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

        releaseResources(orderId);
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

    public void markReady(Long orderId,Long restaurantId) {

        int rows = orderMapper.updateStatusByRestaurant(
                orderId,
                restaurantId,
                OrderStatus.PREPARING.getCode(),
                OrderStatus.READY_FOR_PICKUP.getCode()
        );

        if (rows == 0) {
            throw new BusinessException("订单不存在或当前状态无法标记待取餐");
        }
    }

    public void completeOrder(Long orderId, Long restaurantId, String pickupCode) {
        if (orderMapper.verifyPickup(orderId, restaurantId, pickupCode) == 0)
            throw new BusinessException("取餐码错误、订单不属于该档口或订单非待取餐状态");
    }

    private void releaseResources(Long orderId) {
        releaseSlot(orderId);
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        items.sort(Comparator.comparing(OrderItem::getDishId));
        for (OrderItem item : items) {
            if (dishMapper.increaseStock(item.getDishId(), item.getQuantity()) != 1)
                throw new BusinessException("恢复库存失败, dishId=" + item.getDishId());
        }
    }

    private void releaseSlot(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        // 旧订单没有预约时段，仍允许取消。
        if (order.getPickupSlotId() != null && pickupSlotMapper.release(order.getPickupSlotId()) != 1)
            throw new BusinessException("时段名额释放失败");
    }

    public PageResult<OrderSummaryVO> getMerchantOrders(
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

        PageResult<OrderSummaryVO> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records.stream().map(OrderSummaryVO::from).toList());

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
        vo.setPickupSlotId(order.getPickupSlotId());
        vo.setExpiresAt(order.getExpiresAt());
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

        releaseResources(orderId);

        log.info(
                "订单超时取消成功并恢复库存, orderId={}",
                orderId
        );
    }

}
