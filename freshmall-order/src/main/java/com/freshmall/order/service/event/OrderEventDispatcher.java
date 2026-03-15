package com.freshmall.order.service.event;

import com.freshmall.order.event.OrderEventOutbox;
import com.freshmall.order.mapper.OrderEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventDispatcher.class);

    @Autowired
    private OrderEventOutboxMapper outboxMapper;

    @Autowired
    private OrderEventProcessor processor;

    @Scheduled(fixedDelayString = "${order.event.dispatch-interval-ms:1000}")
    public void dispatch() {
        long now = System.currentTimeMillis();
        List<OrderEventOutbox> events = outboxMapper.listDispatchable(now, 100);
        for (OrderEventOutbox event : events) {
            int locked = outboxMapper.markProcessing(event.getId(), now);
            if (locked > 0) {
                processAsync(event);
            }
        }
    }

    @Async
    public void processAsync(OrderEventOutbox event) {
        try {
            processor.process(event);
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            long nextRetryTime = now + 5000L;
            outboxMapper.markRetry(event.getId(), nextRetryTime, now);
            logger.error("订单事件处理失败，将重试。eventId={}, error={}", event.getEventId(), ex.getMessage());
        }
    }
}
