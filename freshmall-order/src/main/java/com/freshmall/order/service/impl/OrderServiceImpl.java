package com.freshmall.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Order;
import com.freshmall.common.entity.Thing;
import com.freshmall.common.entity.User;
import com.freshmall.common.exception.BizException;
import com.freshmall.order.constant.OrderStatusConstants;
import com.freshmall.order.event.OrderEventType;
import com.freshmall.order.event.OrderStockEventPayload;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.feign.UserFeignClient;
import com.freshmall.order.mapper.CartMapper;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    CartMapper cartMapper;

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
        String paymentNo = generatePaymentNo();
        return createOrder(order, paymentNo);
    }

    private Order createOrder(Order order, String paymentNo) {
        validateCreateOrder(order);
        int count = Integer.parseInt(order.getCount());

        // 1. 内存高速预扣减：调用远程商品服务预扣 Redis 缓存库存，拦截非法的高并发超卖请求
        APIResponse<?> reserveResp = thingFeignClient.reserveStock(order.getThingId(), count);
        if (reserveResp == null || reserveResp.getCode() != ResponseCode.SUCCESS.getCode()) {
            throw new BizException("库存不足");
        }

        boolean needCompensation = true;
        long ct = System.currentTimeMillis();
        try {
            order.setOrderTime(String.valueOf(ct));
            order.setOrderNumber(generateOrderNumber(ct));
            order.setPaymentNo(paymentNo);
            order.setStatus(OrderStatusConstants.PENDING_PAY);
            // 2. 主业务落库：将订单实体插入数据库（同处于 @Transactional 事务中）
            mapper.insert(order);

            // 3. 生成 Outbox 本地消息表记录：保证本地事务与MQ事件派发的高度一致性
            String eventId = UUID.randomUUID().toString();
            orderEventService.saveOutboxEvent(eventId, order.getId(), OrderEventType.ORDER_CREATED,
                    buildPayload(eventId, order.getId(), order.getThingId(), count));
            needCompensation = false;
            return order;
        } finally {
            if (needCompensation) {
                // 4. 补偿机制：一旦订单写入 DB 或写 Outbox 发件箱失败（或发生其它异常），立即回补并释放已占用 Redis 预扣库存
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
    public Map<String, Object> submitOrder(String mode, String userId, String thingId, Integer count, String cartIds,
            String receiverName, String receiverPhone, String receiverAddress, String remark) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException("用户不能为空");
        }
        if (!StringUtils.hasText(receiverName)) {
            throw new BizException("收货人不能为空");
        }

        String finalMode = normalizeMode(mode, cartIds);

        // 1. 生成全局聚合支付单号：确保无论单品还是购物车多品，都通过单一单号进行统筹支付和状态扭转
        String paymentNo = generatePaymentNo();

        if ("cart".equals(finalMode)) {
            // 2. 购物车分支：校验多条记录及库存，并分拆生成子订单（共享该 paymentNo）
            return submitCartOrder(userId, cartIds, receiverName, receiverPhone, receiverAddress, remark, paymentNo);
        }

        // 3. 直购分支：直接针对单一商品生成订单
        return submitDirectOrder(userId, thingId, count, receiverName, receiverPhone, receiverAddress, remark,
                paymentNo);
    }

    @Override
    @Transactional
    public Map<String, Object> payByPaymentNo(String paymentNo, String userId) {
        if (!StringUtils.hasText(paymentNo)) {
            throw new BizException("paymentNo不能为空");
        }

        // 通过唯一的聚合支付单号找到所有相关的待支付子订单
        List<Order> pendingOrders = mapper.listByPaymentNoAndStatus(paymentNo, OrderStatusConstants.PENDING_PAY,
                userId);
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            throw new BizException("待支付订单不存在或已支付");
        }

        String payTime = String.valueOf(System.currentTimeMillis());
        // 统一流转所有处于 PENDING_PAY 状态的子订单到 TO_SHIP（待发货），保证同批次车购物车支付状态一致
        int affected = mapper.payByPaymentNo(paymentNo, OrderStatusConstants.PENDING_PAY, OrderStatusConstants.TO_SHIP,
                payTime, userId);
        if (affected <= 0) {
            throw new BizException("支付失败，请重试");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("paymentNo", paymentNo);
        data.put("paidCount", affected);
        data.put("payTime", payTime);
        data.put("nextStatus", OrderStatusConstants.TO_SHIP);
        data.put("orderIds", pendingOrders.stream().map(Order::getId).collect(Collectors.toList()));
        return data;
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
        // 生成取消订单的 Outbox 事件（ORDER_CANCELED），后续 MQ 消费者将基于此执行对应商品库存的回补和释放操作
        orderEventService.saveOutboxEvent(eventId, order.getId(), OrderEventType.ORDER_CANCELED,
                buildPayload(eventId, order.getId(), order.getThingId(), count));
    }

    private void validateCreateOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getThingId()) || !StringUtils.hasText(order.getUserId())) {
            throw new BizException("参数错误");
        }
        int count = parsePositiveInt(order.getCount(), -1);
        if (count <= 0) {
            throw new BizException("商品数量不合法");
        }

        Thing thing = thingFeignClient.getThingById(order.getThingId());
        if (thing == null) {
            throw new BizException("商品不存在");
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

    private Map<String, Object> submitDirectOrder(String userId, String thingId, Integer count, String receiverName,
            String receiverPhone, String receiverAddress, String remark, String paymentNo) {
        if (!StringUtils.hasText(thingId)) {
            throw new BizException("商品不能为空");
        }
        int finalCount = (count == null || count <= 0) ? 1 : count;

        Order order = new Order();
        order.setUserId(userId);
        order.setThingId(thingId);
        order.setCount(String.valueOf(finalCount));
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        Order created = createOrder(order, paymentNo);

        BigDecimal totalAmount = BigDecimal.ZERO;
        Thing thing = thingFeignClient.getThingById(thingId);
        if (thing != null && StringUtils.hasText(thing.getPrice())) {
            totalAmount = new BigDecimal(thing.getPrice()).multiply(BigDecimal.valueOf(finalCount));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("paymentNo", paymentNo);
        data.put("orderCount", 1);
        data.put("totalAmount", totalAmount);
        data.put("orderIds", Collections.singletonList(created.getId()));
        data.put("firstOrderId", created.getId());
        data.put("orderNumbers", Collections.singletonList(created.getOrderNumber()));
        return data;
    }

    private Map<String, Object> submitCartOrder(String userId, String cartIds, String receiverName,
            String receiverPhone,
            String receiverAddress, String remark, String paymentNo) {
        List<Long> selectedIds = parseCartIds(cartIds);
        if (selectedIds.isEmpty()) {
            throw new BizException("请至少选择一条购物车记录");
        }

        List<com.freshmall.common.entity.Cart> carts = cartMapper.getUserCartListByIds(userId, selectedIds);
        if (carts.size() != selectedIds.size()) {
            throw new BizException("购物车记录不存在或无权限");
        }

        Map<String, Thing> thingMap = new HashMap<>();
        for (com.freshmall.common.entity.Cart cart : carts) {
            Thing thing = thingFeignClient.getThingById(cart.getThingId());
            if (thing == null) {
                throw new BizException("存在已下架商品");
            }
            if (cart.getCount() > thing.getRepertory()) {
                throw new BizException("商品库存不足: " + thing.getTitle());
            }
            thingMap.put(cart.getThingId(), thing);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Long> orderIds = new ArrayList<>();
        List<String> orderNumbers = new ArrayList<>();

        for (com.freshmall.common.entity.Cart cart : carts) {
            Order order = new Order();
            order.setUserId(userId);
            order.setThingId(cart.getThingId());
            order.setCount(String.valueOf(cart.getCount()));
            order.setReceiverName(receiverName);
            order.setReceiverPhone(receiverPhone);
            order.setReceiverAddress(receiverAddress);
            order.setRemark(remark);

            Order created = createOrder(order, paymentNo);
            orderIds.add(created.getId());
            orderNumbers.add(created.getOrderNumber());

            Thing thing = thingMap.get(cart.getThingId());
            BigDecimal price = new BigDecimal(thing.getPrice());
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(cart.getCount())));
        }

        cartMapper.deleteBatchIds(selectedIds);

        Map<String, Object> data = new HashMap<>();
        data.put("paymentNo", paymentNo);
        data.put("orderCount", orderIds.size());
        data.put("totalAmount", totalAmount);
        data.put("cartIds", selectedIds);
        data.put("orderIds", orderIds);
        data.put("firstOrderId", orderIds.isEmpty() ? null : orderIds.get(0));
        data.put("orderNumbers", orderNumbers);
        return data;
    }

    private List<Long> parseCartIds(String cartIds) {
        if (!StringUtils.hasText(cartIds)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        String[] arr = cartIds.split(",");
        for (String raw : arr) {
            String trimmed = raw == null ? "" : raw.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ex) {
                throw new BizException("购物车ID格式错误");
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private String normalizeMode(String mode, String cartIds) {
        if (StringUtils.hasText(mode)) {
            String normalized = mode.trim().toLowerCase(Locale.ROOT);
            if ("direct".equals(normalized) || "cart".equals(normalized)) {
                return normalized;
            }
            throw new BizException("mode仅支持direct或cart");
        }
        return StringUtils.hasText(cartIds) ? "cart" : "direct";
    }

    private String generatePaymentNo() {
        long now = System.currentTimeMillis();
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "PAY" + now + suffix;
    }

    private String generateOrderNumber(long now) {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "O" + now + suffix;
    }
}
