package com.freshmall.order.service.event;

import com.freshmall.common.mq.OrderMqConstants;
import com.freshmall.common.mq.OrderStockEventMessage;
import com.freshmall.order.event.OrderEventOutbox;
import com.freshmall.order.mapper.OrderEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventDispatcher.class);

    @Autowired
    private OrderEventOutboxMapper outboxMapper;

    @Autowired
    private OrderEventService orderEventService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${order.event.max-retry:12}")
    private int maxRetry;

    @Scheduled(fixedDelayString = "${order.event.dispatch-interval-ms:1000}")
    public void dispatch() {
        long now = System.currentTimeMillis();
        List<OrderEventOutbox> events = outboxMapper.listDispatchable(now, 100);
        for (OrderEventOutbox event : events) {
            int locked = outboxMapper.markProcessing(event.getId(), now);
            if (locked > 0) {
                relay(event);
            }
        }
    }

    private void relay(OrderEventOutbox event) {
        try {
            OrderStockEventMessage message = orderEventService.buildMessage(event);
            rabbitTemplate.convertAndSend(
                    OrderMqConstants.STOCK_EVENT_EXCHANGE,
                    OrderMqConstants.STOCK_EVENT_ROUTING_KEY,
                    message);
            outboxMapper.markDone(event.getId(), System.currentTimeMillis());
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            int nextRetryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            if (nextRetryCount >= maxRetry) {
                outboxMapper.markDead(event.getId(), now);
                logger.error("订单事件进入死信状态。eventId={}, retryCount={}, error={}", event.getEventId(),
                        nextRetryCount, ex.getMessage());
                return;
            }

            long interval = Math.min(60_000L, 5_000L * (1L << Math.min(nextRetryCount - 1, 3)));
            long nextRetryTime = now + interval;
            outboxMapper.markRetry(event.getId(), nextRetryTime, now);
            logger.error("订单事件投递失败，将重试。eventId={}, retryCount={}, error={}", event.getEventId(), nextRetryCount,
                    ex.getMessage());
        }
    }
}
