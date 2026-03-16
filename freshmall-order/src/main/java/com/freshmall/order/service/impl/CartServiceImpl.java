package com.freshmall.order.service.impl;

import com.freshmall.common.entity.Cart;
import com.freshmall.common.entity.Thing;
import com.freshmall.common.exception.BizException;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.mapper.CartMapper;
import com.freshmall.order.service.CartService;
import com.freshmall.order.service.OrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
            throw new BizException("用户不能为空");
        }
        List<Cart> list = cartMapper.getUserCartList(userId);
        fillThingInfo(list);
        return list;
    }

    @Override
    @Transactional
    public void addCart(String userId, String thingId, Integer count) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(thingId)) {
            throw new BizException("参数不完整");
        }
        int finalCount = (count == null || count < 1) ? 1 : count;

        Thing thing = thingFeignClient.getThingById(thingId);
        if (thing == null) {
            throw new BizException("商品不存在");
        }
        if (thing.getRepertory() <= 0) {
            throw new BizException("商品库存不足");
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
            throw new BizException("参数不合法");
        }
        Cart cart = checkCartOwnership(userId, id);
        Thing thing = thingFeignClient.getThingById(cart.getThingId());
        if (thing == null) {
            throw new BizException("商品不存在");
        }
        if (count > thing.getRepertory()) {
            throw new BizException("超出库存上限");
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
        return orderService.submitOrder("cart", userId, null, null, ids, receiverName, receiverPhone,
                receiverAddress, remark);
    }

    private Cart checkCartOwnership(String userId, Long id) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BizException("购物车记录不存在");
        }
        if (!StringUtils.equals(userId, cart.getUserId())) {
            throw new BizException("无权限操作该购物车记录");
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
