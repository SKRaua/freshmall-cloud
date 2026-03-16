package com.freshmall.common.mq;

public final class OrderMqConstants {

    private OrderMqConstants() {
    }

    public static final String STOCK_EVENT_EXCHANGE = "freshmall.order.stock.exchange";
    public static final String STOCK_EVENT_ROUTING_KEY = "order.stock.event";
    public static final String STOCK_EVENT_QUEUE = "freshmall.stock.event.queue";

    public static final String STOCK_EVENT_DLX = "freshmall.order.stock.dlx";
    public static final String STOCK_EVENT_DLQ_ROUTING_KEY = "order.stock.event.dlq";
    public static final String STOCK_EVENT_DLQ = "freshmall.stock.event.dlq";
}
