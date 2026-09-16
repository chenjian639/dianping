package com.dianping.consumer;

import com.dianping.config.RabbitMQConfig;
import com.dianping.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    public OrderTimeoutConsumer(
            OrderService orderService) {

        this.orderService = orderService;
    }

    @RabbitListener(
            queues = RabbitMQConfig.ORDER_TIMEOUT_QUEUE
    )
    public void handleOrderTimeout(Long orderId) {

        log.info(
                "收到订单超时检查消息, orderId={}",
                orderId
        );

        try {

            orderService.timeoutCancelOrder(orderId);

        } catch (Exception e) {

            log.error(
                    "订单超时处理失败, orderId={}",
                    orderId,
                    e
            );

            // 一定重新抛出去
            // Spring AMQP才能知道消费失败
            throw e;
        }
    }
}