package com.freshmall.order.event;

import lombok.Data;

@Data
public class OrderStockEventPayload {
    private String eventId;
    private Long orderId;
    private String thingId;
    private int count;
}
