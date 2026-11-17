package com.trade.service;
import com.trade.entity.*;
import com.trade.mapper.*;
import com.trade.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 支付宝支付与本地交易状态同步。 */
@Service
@RequiredArgsConstructor
public class PaymentService {
    public List<com.trade.vo.AdminPaymentVO> pendingForAdmin() {
        return payments.pendingForAdmin();
    }
    private final AlipayGateway gateway;
    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final OrderItemMapper items;
    private final ProductMapper products;
    private final InventoryService inventory;

    @Transactional
    public Map<String, String> checkout(Long userId, Long orderId) {
        Order order = ownedOrder(userId, orderId);
        if (order.getStatus() != 0 || !orders.isPayable(orderId)) throw new BusinessException("订单已关闭或超过支付截止时间");
        Payment payment = create(userId, orderId);
        requireAlipay(payment);
        return Map.of("paymentUrl", gateway.page(payment, order.getExpiresAt()));
    }

    @Transactional
    public void notifyPayment(Map<String,String> params) {
        if (!gateway.verify(params)) throw new BusinessException("支付通知验签失败");
        if (!Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(String.valueOf(params.get("trade_status")))) return;
        String tradeNo = params.get("out_trade_no");
        if (tradeNo == null || !tradeNo.matches("trade-[0-9]+")) throw new BusinessException("无效支付单号");
        Long id = Long.valueOf(tradeNo.substring(6));
        requireAlipay(payments.byId(id));
        callback(id, params.get("trade_no"), new BigDecimal(params.get("total_amount")));
    }

    public boolean closeBeforeCancel(Order order) {
        Payment payment = payments.byOrder(order.getId());
        if (payment == null) return true;
        requireAlipay(payment);
        var result = gateway.query(payment);
        if (Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(String.valueOf(result.getTradeStatus()))) {
            callback(payment.getId(), result.getTradeNo(), new BigDecimal(result.getTotalAmount()));
            return false;
        }
        if (!"TRADE_CLOSED".equals(result.getTradeStatus())) gateway.close(payment, !orders.isPayable(order.getId()));
        return true;
    }

    private void requireAlipay(Payment payment) {
        if (payment == null || !"ALIPAY".equals(payment.getChannel()))
            throw new BusinessException("历史模拟支付单不能用于真实支付，请先结清历史订单");
    }

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
        payment.setChannel("ALIPAY");
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
        if (reference == null || reference.isBlank()) throw new BusinessException("渠道流水不能为空");
        if (amount==null || amount.compareTo(payment.getAmount())!=0)
            throw new BusinessException("回调金额不匹配");
        if (!payment.getStatus().equals("PENDING")) {
            if (!Objects.equals(reference,payment.getChannelReference())) throw new BusinessException("渠道流水不匹配");
            return;
        }
        // 验签通知允许晚到；取消流程必须先确认渠道关单。
        if (orders.transition(order.getId(),0,1)!=1)
            throw new BusinessException("订单已关闭或超过支付截止时间");
        if (payments.succeed(paymentId,reference)!=1) throw new BusinessException("支付状态冲突");
    }

    private Order ownedOrder(Long userId,Long orderId) {
        Order order=orders.lockById(orderId);
        if (order==null || !Objects.equals(order.getUserId(),userId)) throw new BusinessException("订单不存在");
        return order;
    }
}
