package com.dianping.service;
import com.dianping.entity.*;
import com.dianping.mapper.*;
import com.dianping.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 模拟渠道没有外部资金操作；订单、支付、退款和库存写入同一数据库事务。 */
@Service
@RequiredArgsConstructor
public class PaymentService {
    public List<com.dianping.vo.AdminPaymentVO> pendingForAdmin() {
        return payments.pendingForAdmin();
    }
    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final OrderItemMapper items;
    private final ProductMapper products;
    private final InventoryService inventory;

    @Transactional
    public Payment create(Long userId,Long orderId) {
        Order order=ownedOrder(userId,orderId);
        Payment existing=payments.byOrder(orderId);
        if (existing!=null) return existing;
        if (order.getStatus()!=0) throw new BusinessException("仅待支付订单可创建支付单");
        Payment payment=new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus("PENDING");
        payments.insert(payment);
        return payment;
    }

    @Transactional
    public void callback(Long paymentId,String reference,BigDecimal amount) {
        Payment initial=payments.byId(paymentId);
        if (initial==null) throw new BusinessException("支付单不存在");
        // 所有交易操作先锁订单，再处理支付单和商品，避免相反锁顺序。
        Order order=orders.lockById(initial.getOrderId());
        Payment payment=payments.byOrder(order.getId());
        if (amount==null || amount.compareTo(payment.getAmount())!=0)
            throw new BusinessException("回调金额不匹配");
        if (!payment.getStatus().equals("PENDING")) {
            if (!Objects.equals(reference,payment.getChannelReference())) throw new BusinessException("渠道流水不匹配");
            return;
        }
        // 数据库时间裁决截止时间。模拟渠道拒绝晚到支付；真实渠道需另设退款补偿。
        if (orders.payOrder(order.getId(),order.getUserId(),0,1)!=1)
            throw new BusinessException("订单已关闭或超过支付截止时间");
        if (payments.succeed(paymentId,reference)!=1) throw new BusinessException("支付状态冲突");
    }

    @Transactional
    public void refund(Long userId,Long orderId) {
        Order order=ownedOrder(userId,orderId);
        if (order.getStatus()==6) return;
        if (order.getStatus()!=1) throw new BusinessException("仅未发货的已支付订单支持整单退款");
        Payment payment=payments.byOrder(orderId);
        if (payment==null || payments.refund(payment.getId())!=1) throw new BusinessException("没有可退款的成功支付单");
        payments.insertRefund(payment);
        if (orders.transition(orderId,1,6)!=1) throw new BusinessException("订单状态冲突");
        List<OrderItem> lines=items.selectByOrderId(orderId);
        lines.sort(Comparator.comparing(OrderItem::getDishId));
        for (OrderItem line:lines) {
            if (products.increaseStock(line.getDishId(),line.getQuantity())!=1)
                throw new BusinessException("退款库存恢复失败");
            inventory.record(line.getDishId(),line.getQuantity(),"REFUND",String.valueOf(orderId));
        }
    }

    private Order ownedOrder(Long userId,Long orderId) {
        Order order=orders.lockById(orderId);
        if (order==null || !Objects.equals(order.getUserId(),userId)) throw new BusinessException("订单不存在");
        return order;
    }
}
