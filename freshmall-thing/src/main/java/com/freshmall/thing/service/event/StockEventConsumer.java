package com.freshmall.thing.service.event;

import com.freshmall.common.mq.OrderMqConstants;
import com.freshmall.common.mq.OrderStockEventMessage;
import com.freshmall.thing.event.StockEventConsumeLog;
import com.freshmall.thing.mapper.StockEventConsumeLogMapper;
import com.freshmall.thing.service.ThingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockEventConsumer.class);
    private static final String ORDER_CREATED = "ORDER_CREATED";
    private static final String ORDER_CANCELED = "ORDER_CANCELED";

    private final ThingService thingService;
    private final StockEventConsumeLogMapper consumeLogMapper;

    public StockEventConsumer(ThingService thingService, StockEventConsumeLogMapper consumeLogMapper) {
        this.thingService = thingService;
        this.consumeLogMapper = consumeLogMapper;
    }

    @RabbitListener(queues = OrderMqConstants.STOCK_EVENT_QUEUE, containerFactory = "stockEventListenerContainerFactory")
    @Transactional
    public void consume(OrderStockEventMessage message) {
        if (message == null || message.getEventId() == null || message.getOrderId() == null) {
            throw new IllegalArgumentException("库存事件消息不合法");
        }

        if (!tryStartConsume(message)) {
            logger.info("库存事件重复消费，跳过。eventId={}", message.getEventId());
            return;
        }

        try {
            boolean success;
            if (ORDER_CREATED.equals(message.getEventType())) {
                success = thingService.confirmDeductStock(message.getThingId(), message.getCount());
            } else if (ORDER_CANCELED.equals(message.getEventType())) {
                success = thingService.releaseStock(message.getThingId(), message.getCount());
            } else {
                logger.warn("未知库存事件类型，忽略。eventId={}, eventType={}", message.getEventId(), message.getEventType());
                success = true;
            }

            if (!success) {
                throw new IllegalStateException("库存事件处理失败");
            }

            consumeLogMapper.markSuccess(message.getEventId(), System.currentTimeMillis());
        } catch (Exception ex) {
            consumeLogMapper.deleteByEventId(message.getEventId());
            throw ex;
        }
    }

    private boolean tryStartConsume(OrderStockEventMessage message) {
        StockEventConsumeLog log = new StockEventConsumeLog();
        long now = System.currentTimeMillis();
        log.setEventId(message.getEventId());
        log.setOrderId(message.getOrderId());
        log.setEventType(message.getEventType());
        log.setStatus("PROCESSING");
        log.setCreateTime(now);
        log.setUpdateTime(now);

        try {
            consumeLogMapper.insert(log);
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            String status = consumeLogMapper.findStatusByEventId(message.getEventId());
            logger.info("库存事件消费记录已存在，跳过重复消费。eventId={}, status={}", message.getEventId(), status);
            return false;
        }
    }
}
