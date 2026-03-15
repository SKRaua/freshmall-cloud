package com.freshmall.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Order;
import com.freshmall.common.entity.Thing;
import com.freshmall.common.entity.User;
import com.freshmall.order.constant.OrderStatusConstants;
import com.freshmall.order.event.OrderEventType;
import com.freshmall.order.event.OrderStockEventPayload;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.feign.UserFeignClient;
import com.freshmall.order.mapper.OrderMapper;
import com.freshmall.order.service.OrderService;
import com.freshmall.order.service.event.OrderEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    OrderEventService orderEventService;

    @Value("${order.timeout.pending-pay-ms:1800000}")
    private long pendingPayTimeoutMs;

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
    @Transactional
    public Order createOrder(Order order) {
        validateCreateOrder(order);
        int count = Integer.parseInt(order.getCount());

        APIResponse<?> reserveResp = thingFeignClient.reserveStock(order.getThingId(), count);
        if (reserveResp == null || reserveResp.getCode() != ResponseCode.SUCCESS.getCode()) {
            throw new IllegalArgumentException("库存不足");
        }

        boolean needCompensation = true;
        long ct = System.currentTimeMillis();
        try {
            order.setOrderTime(String.valueOf(ct));
            order.setOrderNumber(String.valueOf(ct));
            order.setStatus(OrderStatusConstants.PENDING_PAY);
            mapper.insert(order);

            String eventId = UUID.randomUUID().toString();
            orderEventService.saveOutboxEvent(eventId, order.getId(), OrderEventType.ORDER_CREATED,
                    buildPayload(eventId, order.getId(), order.getThingId(), count));
            needCompensation = false;
            return order;
        } finally {
            if (needCompensation) {
                try {
                    thingFeignClient.unreserveStock(order.getThingId(), count);
                } catch (Exception ex) {
                    logger.error("创建订单失败后回补缓存库存失败，thingId={}, count={}", order.getThingId(), count);
                }
            }
        }
    }

    @Override
    @Transactional
    public boolean cancelOrderByAdmin(Long id) {
        if (id == null) {
            return false;
        }
        Order dbOrder = mapper.selectById(id);
        if (dbOrder == null) {
            return false;
        }
        return closeOrderAndEmitCancelEvent(dbOrder);
    }

    @Override
    @Transactional
    public boolean cancelOrderByUser(Long id, String userId) {
        if (id == null || !StringUtils.hasText(userId)) {
            return false;
        }
        Order dbOrder = mapper.selectById(id);
        if (dbOrder == null || !userId.equals(dbOrder.getUserId())) {
            return false;
        }
        return closeOrderAndEmitCancelEvent(dbOrder);
    }

    @Override
    @Transactional
    public int closeTimeoutPendingPayOrders() {
        long deadline = System.currentTimeMillis() - pendingPayTimeoutMs;
        List<Order> timeoutOrders = mapper.listTimeoutOrders(OrderStatusConstants.PENDING_PAY, deadline, 100);
        int affected = 0;
        for (Order timeoutOrder : timeoutOrders) {
            int updated = mapper.updateStatusIfCurrent(timeoutOrder.getId(), OrderStatusConstants.PENDING_PAY,
                    OrderStatusConstants.CANCELED);
            if (updated > 0) {
                affected++;
                enqueueCancelEvent(timeoutOrder);
            }
        }
        return affected;
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

    private boolean closeOrderAndEmitCancelEvent(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        if (!OrderStatusConstants.PENDING_PAY.equals(order.getStatus())
                && !OrderStatusConstants.TO_SHIP.equals(order.getStatus())) {
            return false;
        }

        int updated = mapper.updateStatusIfCurrent(order.getId(), order.getStatus(), OrderStatusConstants.CANCELED);
        if (updated <= 0) {
            return false;
        }
        enqueueCancelEvent(order);
        return true;
    }

    private void enqueueCancelEvent(Order order) {
        int count = parsePositiveInt(order.getCount(), 0);
        if (count <= 0) {
            logger.warn("订单数量异常，跳过取消事件。orderId={}", order.getId());
            return;
        }
        String eventId = UUID.randomUUID().toString();
        orderEventService.saveOutboxEvent(eventId, order.getId(), OrderEventType.ORDER_CANCELED,
                buildPayload(eventId, order.getId(), order.getThingId(), count));
    }

    private void validateCreateOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getThingId()) || !StringUtils.hasText(order.getUserId())) {
            throw new IllegalArgumentException("参数错误");
        }
        int count = parsePositiveInt(order.getCount(), -1);
        if (count <= 0) {
            throw new IllegalArgumentException("商品数量不合法");
        }

        Thing thing = thingFeignClient.getThingById(order.getThingId());
        if (thing == null) {
            throw new IllegalArgumentException("商品不存在");
        }
    }

    private int parsePositiveInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private OrderStockEventPayload buildPayload(String eventId, Long orderId, String thingId, int count) {
        OrderStockEventPayload payload = new OrderStockEventPayload();
        payload.setEventId(eventId);
        payload.setOrderId(orderId);
        payload.setThingId(thingId);
        payload.setCount(count);
        return payload;
    }
}
