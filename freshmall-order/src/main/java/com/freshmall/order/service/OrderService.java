package com.freshmall.order.service;

import com.freshmall.common.entity.Order;

import java.util.List;
import java.util.Map;

public interface OrderService {
    List<Order> getOrderList(String orderNumber, String username, String status, String startTime, String endTime);

    Order createOrder(Order order);

    Map<String, Object> submitOrder(String mode, String userId, String thingId, Integer count, String cartIds,
            String receiverName, String receiverPhone, String receiverAddress, String remark);

    Map<String, Object> payByPaymentNo(String paymentNo, String userId);

    boolean cancelOrderByAdmin(Long id);

    boolean cancelOrderByUser(Long id, String userId);

    int closeTimeoutPendingPayOrders();

    void deleteOrder(String id);

    void updateOrder(Order order);

    List<Order> getUserOrderList(String userId, String status);
}
