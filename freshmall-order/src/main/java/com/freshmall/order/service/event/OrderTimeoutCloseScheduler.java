package com.freshmall.order.service.event;

import com.freshmall.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutCloseScheduler {

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedDelayString = "${order.timeout.scan-interval-ms:60000}")
    public void closeTimeoutOrders() {
        orderService.closeTimeoutPendingPayOrders();
    }
}
