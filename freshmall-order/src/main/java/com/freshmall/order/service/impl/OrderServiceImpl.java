package com.freshmall.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.freshmall.common.entity.Order;
import com.freshmall.common.entity.Thing;
import com.freshmall.common.entity.User;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.feign.UserFeignClient;
import com.freshmall.order.mapper.OrderMapper;
import com.freshmall.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    OrderMapper mapper;

    @Autowired
    ThingFeignClient thingFeignClient;

    @Autowired
    UserFeignClient userFeignClient;

    @Override
    public List<Order> getOrderList(String orderNumber, String username, String status, String startTime,
            String endTime) {
        List<Order> orders = mapper.getList(orderNumber, status, startTime, endTime);
        enrichOrders(orders);
        if (StringUtils.hasText(username)) {
            String keyword = username.trim().toLowerCase();
            orders = orders.stream()
                    .filter(order -> order.getUsername() != null && order.getUsername().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
        }
        return orders;
    }

    @Override
    public void createOrder(Order order) {
        long ct = System.currentTimeMillis();
        order.setOrderTime(String.valueOf(ct));
        order.setOrderNumber(String.valueOf(ct));
        order.setStatus("1");
        mapper.insert(order);
    }

    @Override
    public void deleteOrder(String id) {
        mapper.deleteById(id);
    }

    @Override
    public void updateOrder(Order order) {
        mapper.updateById(order);
    }

    @Override
    public List<Order> getUserOrderList(String userId, String status) {
        List<Order> orders = mapper.getUserOrderList(userId, status);
        enrichOrders(orders);
        return orders;
    }

    /**
     * 通过 Feign 远程调用补充订单中的用户名、商品名、封面、价格等信息
     * 避免在 Mapper 中跨表 JOIN（保持微服务数据边界独立）
     */
    private void enrichOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        // 收集去重的 thingId 和 userId
        Set<String> thingIds = orders.stream()
                .map(Order::getThingId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> userIds = orders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 批量通过 Feign 获取商品信息（逐个调用，数据量小时可接受；后续可加批量接口优化）
        Map<String, Thing> thingMap = new HashMap<>();
        for (String thingId : thingIds) {
            try {
                Thing thing = thingFeignClient.getThingById(thingId);
                if (thing != null) {
                    thingMap.put(thingId, thing);
                }
            } catch (Exception e) {
                logger.warn("Feign 调用获取商品失败, thingId={}: {}", thingId, e.getMessage());
            }
        }

        // 批量通过 Feign 获取用户信息
        Map<String, User> userMap = new HashMap<>();
        for (String userId : userIds) {
            try {
                User user = userFeignClient.getUserById(userId);
                if (user != null) {
                    userMap.put(userId, user);
                }
            } catch (Exception e) {
                logger.warn("Feign 调用获取用户失败, userId={}: {}", userId, e.getMessage());
            }
        }

        // 填充订单中的冗余字段
        for (Order order : orders) {
            Thing thing = thingMap.get(order.getThingId());
            if (thing != null) {
                order.setTitle(thing.getTitle());
                order.setCover(thing.getCover());
                order.setPrice(thing.getPrice());
            }
            User user = userMap.get(order.getUserId());
            if (user != null) {
                order.setUsername(user.getUsername());
            }
        }
    }
}
