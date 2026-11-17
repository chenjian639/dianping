package com.trade.service;

import com.trade.vo.OrderSummaryVO;

import com.trade.common.PageResult;
import com.trade.entity.*;
import com.trade.enums.OrderStatus;
import com.trade.exception.BusinessException;
import com.trade.mapper.*;
import com.trade.vo.CartItemVO;
import com.trade.vo.OrderDetailVO;
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
    private final PaymentService paymentService;

    private final ProductMapper productMapper;

    private final OrderMapper orderMapper;

    private final UserMapper userMapper;

    private final InventoryService inventory;
    private final MerchantMapper merchants;

    private final OrderItemMapper orderItemMapper;

    private final OutboxEventService outboxEventService;

    @Transactional
    public Long createOrder(
            Long userId,
            String requestId, String shippingAddress) {

        if (requestId == null || requestId.isBlank() || requestId.length() > 64 || shippingAddress == null || shippingAddress.isBlank() || shippingAddress.length() > 500) {
            throw new BusinessException("requestId须为1到64字符且必须填写收货地址");
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
            if (!Objects.equals(existing.getShippingAddress(), shippingAddress)) throw new BusinessException("幂等键已用于不同收货地址");
            return existing.getId();
        }

        // 2. 查询购物车
        List<CartItemVO> cartItems = cartItemMapper.selectCartByUserId(userId);

        if (cartItems.isEmpty()) {
            throw new BusinessException("购物车为空");
        }

        // 3. 固定库存锁顺序
        cartItems.sort(Comparator.comparing(CartItemVO::getProductId));

        BigDecimal totalAmount = BigDecimal.ZERO;

        Long merchantId = null;

        Map<Long, Product> productsById = new HashMap<>();

        // 按固定顺序锁商品；锁内读取价格，直到库存扣减和订单快照一起提交。
        for (CartItemVO cartItem : cartItems) {
            if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1)
                throw new BusinessException("购物车数量不合法");

            Product product = productMapper.lockById(cartItem.getProductId());

            if (product == null ||
                    product.getStatus() != 1) {

                throw new BusinessException("存在无效或下架商品");
            }

            productsById.put(
                    product.getId(),
                    product
            );

            if (merchantId == null) {

                merchantId = product.getMerchantId();

            } else if (!merchantId.equals(
                    product.getMerchantId())) {

                throw new BusinessException("一次订单只能购买同一家商家的商品");
            }

            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        if (totalAmount.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BusinessException("订单金额超出上限");
        Merchant merchant = merchants.selectById(merchantId);
        if (merchant == null || merchant.getStatus() != 1) throw new BusinessException("商家已停业");
        // 5. 先写订单，抢占幂等键
        Order order = new Order();

        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setRequestId(requestId);
        order.setShippingAddress(shippingAddress);
        order.setGoodsAmount(totalAmount);
        order.setFreightAmount(BigDecimal.ZERO);

        try {

            orderMapper.insert(order);

        } catch (DuplicateKeyException e) {

            throw new BusinessException("订单正在创建，请勿重复提交");
        }

        // 6. 原子扣库存 + 创建快照
        for (CartItemVO cartItem : cartItems) {

            Product product = productsById.get(cartItem.getProductId());

            int rows =
                    productMapper.decreaseStock(
                            product.getId(),
                            cartItem.getQuantity()
                    );

            if (rows == 0) {
                throw new BusinessException(product.getName() + "库存不足");
            }

            OrderItem orderItem = new OrderItem();

            orderItem.setOrderId(order.getId());
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());

            orderItemMapper.insert(orderItem);
            inventory.record(product.getId(), -cartItem.getQuantity(), "ORDER", String.valueOf(order.getId()));
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

        Order order = orderMapper.selectByIdAndUserId(orderId, userId);

        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);

        OrderDetailVO vo = new OrderDetailVO();

        vo.setId(order.getId());
        vo.setExpiresAt(order.getExpiresAt());
        vo.setMerchantId(order.getMerchantId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setGoodsAmount(order.getGoodsAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setShippingAddress(order.getShippingAddress());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items);

        return vo;
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId) || order.getStatus() != 0)
            throw new BusinessException("订单不存在或当前状态无法取消");
        if (!paymentService.closeBeforeCancel(order)) return;


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

    @Transactional
    public void ship(Long orderId, Long merchantId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || !Objects.equals(order.getMerchantId(), merchantId))
            throw new BusinessException("订单不存在或无权限");
        if (orderMapper.transition(orderId, 1, 3) != 1) throw new BusinessException("仅已支付订单可发货");
    }

    @Transactional
    public void receive(Long orderId, Long userId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException("订单不存在");
        if (order.getStatus() == 4) return;
        if (orderMapper.transition(orderId, 3, 4) != 1) throw new BusinessException("仅已发货订单可确认收货");
    }

    private void releaseResources(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        items.sort(Comparator.comparing(OrderItem::getProductId));
        for (OrderItem item : items) {
            if (productMapper.increaseStock(item.getProductId(), item.getQuantity()) != 1)
                throw new BusinessException("恢复库存失败, productId=" + item.getProductId());
            inventory.record(item.getProductId(), item.getQuantity(), "CANCEL", String.valueOf(orderId));
        }
    }

    public PageResult<OrderSummaryVO> getMerchantOrders(
            Long merchantId,
            Integer page,
            Integer pageSize,
            Integer status) {

        int offset = (page - 1) * pageSize;

        List<Order> records =
                orderMapper.selectMerchantOrdersPage(
                        merchantId,
                        status,
                        offset,
                        pageSize
                );

        Long total =
                orderMapper.countMerchantOrders(
                        merchantId,
                        status
                );

        PageResult<OrderSummaryVO> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records.stream().map(OrderSummaryVO::from).toList());

        return result;
    }

    public OrderDetailVO getMerchantOrderDetail(
            Long merchantId,
            Long orderId) {

        Order order =
                orderMapper.selectByIdAndMerchantId(
                        orderId,
                        merchantId
                );

        if (order == null) {
            throw new BusinessException("订单不存在或无权限查看");
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);

        OrderDetailVO vo = new OrderDetailVO();

        vo.setId(order.getId());
        vo.setExpiresAt(order.getExpiresAt());
        vo.setMerchantId(order.getMerchantId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setGoodsAmount(order.getGoodsAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setShippingAddress(order.getShippingAddress());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items);

        return vo;
    }

    @Transactional
    public void timeoutCancelOrder(Long orderId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || order.getStatus() != 0 || orderMapper.isPayable(orderId)) return;
        if (!paymentService.closeBeforeCancel(order)) return;


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
