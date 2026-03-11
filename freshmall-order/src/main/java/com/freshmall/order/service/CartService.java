package com.freshmall.order.service;

import com.freshmall.common.entity.Cart;

import java.util.List;
import java.util.Map;

public interface CartService {

    List<Cart> getUserCartList(String userId);

    void addCart(String userId, String thingId, Integer count);

    void updateCartCount(String userId, Long id, Integer count);

    void deleteCart(String userId, Long id);

    Map<String, Object> checkout(String userId, String ids, String receiverName, String receiverPhone,
            String receiverAddress, String remark);
}
