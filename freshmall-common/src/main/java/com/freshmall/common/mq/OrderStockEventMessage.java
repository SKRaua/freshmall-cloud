package com.freshmall.common.mq;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderStockEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private Long orderId;
    private String eventType;
    private String thingId;
    private int count;
}
