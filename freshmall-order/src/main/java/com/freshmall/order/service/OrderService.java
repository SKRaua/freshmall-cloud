package com.freshmall.order.service;

import com.freshmall.common.entity.Order;

import java.util.List;

public interface OrderService {
    List<Order> getOrderList();

    void createOrder(Order order);

    void deleteOrder(String id);

    void updateOrder(Order order);

    List<Order> getUserOrderList(String userId, String status);
}
