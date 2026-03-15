package com.freshmall.order.service.event;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.order.event.OrderEventOutbox;
import com.freshmall.order.event.OrderEventProcessLog;
import com.freshmall.order.event.OrderEventType;
import com.freshmall.order.event.OrderStockEventPayload;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.mapper.OrderEventOutboxMapper;
import com.freshmall.order.mapper.OrderEventProcessLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class OrderEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventProcessor.class);

    @Autowired
    private OrderEventOutboxMapper outboxMapper;

    @Autowired
    private OrderEventProcessLogMapper processLogMapper;

    @Autowired
    private OrderEventService orderEventService;

    @Autowired
    private ThingFeignClient thingFeignClient;

    public void process(OrderEventOutbox outbox) {
        OrderStockEventPayload payload = orderEventService.parsePayload(outbox.getPayload());
        if (payload == null || payload.getOrderId() == null) {
            throw new IllegalArgumentException("订单事件负载不合法");
        }

        if (!tryStartProcess(payload, outbox.getEventType())) {
            logger.info("订单事件已处理，跳过重复消费。orderId={}, eventType={}", payload.getOrderId(), outbox.getEventType());
            outboxMapper.markDone(outbox.getId(), System.currentTimeMillis());
            return;
        }

        if (OrderEventType.ORDER_CREATED.equals(outbox.getEventType())) {
            confirmDeductStock(payload);
            logger.info("订单创建事件处理完成，下游触发占位。orderId={}", payload.getOrderId());
        } else if (OrderEventType.ORDER_CANCELED.equals(outbox.getEventType())) {
            releaseStock(payload);
            logger.info("订单取消事件处理完成，下游撤销占位。orderId={}", payload.getOrderId());
        } else {
            logger.warn("未知订单事件类型，忽略。eventType={}, orderId={}", outbox.getEventType(), payload.getOrderId());
        }

        outboxMapper.markDone(outbox.getId(), System.currentTimeMillis());
    }

    private boolean tryStartProcess(OrderStockEventPayload payload, String eventType) {
        OrderEventProcessLog log = new OrderEventProcessLog();
        log.setEventId(payload.getEventId());
        log.setOrderId(payload.getOrderId());
        log.setEventType(eventType);
        log.setCreateTime(System.currentTimeMillis());
        try {
            processLogMapper.insert(log);
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            return false;
        }
    }

    private void confirmDeductStock(OrderStockEventPayload payload) {
        APIResponse<?> response = thingFeignClient.confirmDeductStock(payload.getThingId(), payload.getCount());
        if (response == null || response.getCode() != ResponseCode.SUCCESS.getCode()) {
            throw new IllegalStateException("正式扣减库存失败");
        }
    }

    private void releaseStock(OrderStockEventPayload payload) {
        APIResponse<?> response = thingFeignClient.releaseStock(payload.getThingId(), payload.getCount());
        if (response == null || response.getCode() != ResponseCode.SUCCESS.getCode()) {
            throw new IllegalStateException("回补库存失败");
        }
    }
}
