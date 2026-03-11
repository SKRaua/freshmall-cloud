package com.freshmall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Cart;
import com.freshmall.common.entity.Order;
import com.freshmall.common.entity.Thing;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.mapper.CartMapper;
import com.freshmall.order.service.CartService;
import com.freshmall.order.service.OrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ThingFeignClient thingFeignClient;

    @Autowired
    private OrderService orderService;

    @Override
    public List<Cart> getUserCartList(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("用户不能为空");
        }
        List<Cart> list = cartMapper.getUserCartList(userId);
        fillThingInfo(list);
        return list;
    }

    @Override
    @Transactional
    public void addCart(String userId, String thingId, Integer count) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(thingId)) {
            throw new IllegalArgumentException("参数不完整");
        }
        int finalCount = (count == null || count < 1) ? 1 : count;

        Thing thing = thingFeignClient.getThingById(thingId);
        if (thing == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (thing.getRepertory() <= 0) {
            throw new IllegalArgumentException("商品库存不足");
        }

        Cart cart = new Cart();
        long now = System.currentTimeMillis();
        cart.setUserId(userId);
        cart.setThingId(thingId);
        cart.setCount(finalCount);
        cart.setCreateTime(String.valueOf(now));
        cart.setUpdateTime(String.valueOf(now));
        cartMapper.insert(cart);
    }

    @Override
    @Transactional
    public void updateCartCount(String userId, Long id, Integer count) {
        if (StringUtils.isBlank(userId) || id == null || count == null || count < 1) {
            throw new IllegalArgumentException("参数不合法");
        }
        Cart cart = checkCartOwnership(userId, id);
        Thing thing = thingFeignClient.getThingById(cart.getThingId());
        if (thing == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (count > thing.getRepertory()) {
            throw new IllegalArgumentException("超出库存上限");
        }

        cart.setCount(count);
        cart.setUpdateTime(String.valueOf(System.currentTimeMillis()));
        cartMapper.updateById(cart);
    }

    @Override
    @Transactional
    public void deleteCart(String userId, Long id) {
        Cart cart = checkCartOwnership(userId, id);
        cartMapper.deleteById(cart.getId());
    }

    @Override
    @Transactional
    public Map<String, Object> checkout(String userId, String ids, String receiverName, String receiverPhone,
            String receiverAddress, String remark) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (StringUtils.isBlank(receiverName)) {
            throw new IllegalArgumentException("收货人不能为空");
        }

        List<Long> selectedIds = parseIds(ids);
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一条购物车记录");
        }

        List<Cart> carts = cartMapper.getUserCartListByIds(userId, selectedIds);
        if (carts.size() != selectedIds.size()) {
            throw new IllegalArgumentException("购物车记录不存在或无权限");
        }

        fillThingInfo(carts);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int orderCount = 0;

        for (Cart cart : carts) {
            Thing thing = thingFeignClient.getThingById(cart.getThingId());
            if (thing == null) {
                throw new IllegalArgumentException("存在已下架商品");
            }
            if (cart.getCount() > thing.getRepertory()) {
                throw new IllegalArgumentException("商品库存不足: " + thing.getTitle());
            }

            APIResponse stockResp = thingFeignClient.deductStock(cart.getThingId(), cart.getCount());
            if (stockResp == null || stockResp.getCode() != ResponseCode.SUCCESS.getCode()) {
                throw new IllegalArgumentException("扣减库存失败: " + thing.getTitle());
            }

            Order order = new Order();
            order.setUserId(userId);
            order.setThingId(cart.getThingId());
            order.setCount(String.valueOf(cart.getCount()));
            order.setReceiverName(receiverName);
            order.setReceiverPhone(receiverPhone);
            order.setReceiverAddress(receiverAddress);
            order.setRemark(remark);
            orderService.createOrder(order);
            orderCount++;

            BigDecimal price = new BigDecimal(thing.getPrice());
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(cart.getCount())));

            cartMapper.deleteById(cart.getId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderCount", orderCount);
        result.put("totalAmount", totalAmount);
        result.put("cartIds", selectedIds);
        return result;
    }

    private Cart checkCartOwnership(String userId, Long id) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new IllegalArgumentException("购物车记录不存在");
        }
        if (!StringUtils.equals(userId, cart.getUserId())) {
            throw new IllegalArgumentException("无权限操作该购物车记录");
        }
        return cart;
    }

    private List<Long> parseIds(String ids) {
        return Arrays.stream(StringUtils.defaultString(ids).split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    private void fillThingInfo(List<Cart> carts) {
        for (Cart cart : carts) {
            Thing thing = thingFeignClient.getThingById(cart.getThingId());
            if (thing != null) {
                cart.setTitle(thing.getTitle());
                cart.setCover(thing.getCover());
                cart.setPrice(thing.getPrice());
                cart.setRepertory(thing.getRepertory());
            }
        }
    }
}
