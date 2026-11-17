package com.trade.service;

import com.trade.entity.*;
import com.trade.mapper.*;
import com.trade.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class RefundService {
    private final TransactionTemplate tx;
    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final OrderItemMapper items;
    private final ProductMapper products;
    private final InventoryService inventory;
    private final AlipayGateway gateway;

    public RefundService(PlatformTransactionManager manager, OrderMapper orders, PaymentMapper payments,
                         OrderItemMapper items, ProductMapper products, InventoryService inventory, AlipayGateway gateway) {
        this.tx = new TransactionTemplate(manager);
        this.orders=orders; this.payments=payments; this.items=items;
        this.products=products; this.inventory=inventory; this.gateway=gateway;
    }

    public void refund(Long userId, Long orderId) {
        Payment payment = tx.execute(status -> {
            Order order=orders.lockById(orderId);
            if (order==null || !Objects.equals(order.getUserId(),userId)) throw new BusinessException("订单不存在");
            if (order.getStatus()==6) return null;
            if (order.getStatus()!=1 && order.getStatus()!=7) throw new BusinessException("仅未发货的已支付订单支持整单退款");
            Payment current=payments.byOrder(orderId);
            if (current==null || !"ALIPAY".equals(current.getChannel()) || !"SUCCEEDED".equals(current.getStatus()))
                throw new BusinessException("没有可退款的支付宝支付单");
            if (order.getStatus()==1 && orders.transition(orderId,1,7)!=1) throw new BusinessException("退款状态冲突");
            return current;
        });
        if (payment==null) return;
        // Durable REFUNDING prevents shipment if the channel succeeds but the local process crashes.
        // The stable out_request_no makes retries safe after an uncertain channel response.
        gateway.refund(payment);
        tx.executeWithoutResult(status -> {
            Order order=orders.lockById(orderId);
            if (order.getStatus()==6) return;
            if (order.getStatus()!=7 || payments.refund(payment.getId())!=1) throw new BusinessException("退款状态冲突");
            payments.insertRefund(payment);
            if (orders.transition(orderId,7,6)!=1) throw new BusinessException("退款状态冲突");
            var lines=items.selectByOrderId(orderId);
            lines.sort(Comparator.comparing(OrderItem::getProductId));
            for (OrderItem line:lines) {
                if (products.increaseStock(line.getProductId(),line.getQuantity())!=1) throw new BusinessException("退款库存恢复失败");
                inventory.record(line.getProductId(),line.getQuantity(),"REFUND",String.valueOf(orderId));
            }
        });
    }

    public void recover() {
        for (Order order:orders.refundingOrders()) {
            try { refund(order.getUserId(),order.getId()); }
            catch (RuntimeException ignored) { /* Remains REFUNDING for the next retry. */ }
        }
    }
}
