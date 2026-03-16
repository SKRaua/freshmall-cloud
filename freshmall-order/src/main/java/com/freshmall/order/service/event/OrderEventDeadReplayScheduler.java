package com.freshmall.order.service.event;

import com.freshmall.order.event.OrderEventOutbox;
import com.freshmall.order.mapper.OrderEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventDeadReplayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventDeadReplayScheduler.class);

    private final OrderEventOutboxMapper outboxMapper;

    @Value("${order.event.dead-replay-enabled:false}")
    private boolean deadReplayEnabled;

    public OrderEventDeadReplayScheduler(OrderEventOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Scheduled(fixedDelayString = "${order.event.dead-replay-interval-ms:60000}")
    public void replayDeadEvents() {
        if (!deadReplayEnabled) {
            return;
        }
        List<OrderEventOutbox> deadEvents = outboxMapper.listDead(100);
        if (deadEvents == null || deadEvents.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int replayed = 0;
        for (OrderEventOutbox deadEvent : deadEvents) {
            replayed += outboxMapper.replayDead(deadEvent.getId(), now);
        }
        if (replayed > 0) {
            logger.info("已回放 DEAD 订单事件数量: {}", replayed);
        }
    }
}
