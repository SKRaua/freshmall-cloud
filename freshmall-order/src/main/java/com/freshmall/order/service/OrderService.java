package com.freshmall.order.service;

import com.freshmall.common.entity.Order;

import java.util.List;

public interface OrderService {
    List<Order> getOrderList(String orderNumber, String username, String status, String startTime, String endTime);

    Order createOrder(Order order);

    boolean cancelOrderByAdmin(Long id);

    boolean cancelOrderByUser(Long id, String userId);

    int closeTimeoutPendingPayOrders();

    void deleteOrder(String id);

    void updateOrder(Order order);

    List<Order> getUserOrderList(String userId, String status);
}
