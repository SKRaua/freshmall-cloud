package com.freshmall.order.service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmall.order.event.OrderEventOutbox;
import com.freshmall.order.event.OrderStockEventPayload;
import com.freshmall.order.mapper.OrderEventOutboxMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderEventService {

    @Autowired
    private OrderEventOutboxMapper outboxMapper;

    @Autowired
    private ObjectMapper objectMapper;

    public void saveOutboxEvent(String eventId, Long orderId, String eventType, OrderStockEventPayload payload) {
        long now = System.currentTimeMillis();
        OrderEventOutbox outbox = new OrderEventOutbox();
        outbox.setEventId(eventId);
        outbox.setOrderId(orderId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setStatus("NEW");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        outboxMapper.insert(outbox);
    }

    public OrderStockEventPayload parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, OrderStockEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析订单事件负载失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化订单事件失败", e);
        }
    }
}
