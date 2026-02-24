package com.freshmall.order.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Order;
import com.freshmall.common.entity.Thing;
import com.freshmall.order.feign.ThingFeignClient;
import com.freshmall.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    OrderService service;

    @Autowired
    ThingFeignClient thingFeignClient;

    // ============================= 查询接口 =============================

    /**
     * 管理员：查询全部订单列表
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public APIResponse list() {
        List<Order> list = service.getOrderList();
        return new APIResponse(ResponseCode.SUCCESS, "查询成功", list);
    }

    /**
     * 用户：查询自己的订单
     */
    @RequestMapping(value = "/userOrderList", method = RequestMethod.GET)
    public APIResponse userOrderList(String userId, String orderStatus) {
        List<Order> list = service.getUserOrderList(userId, orderStatus);
        return new APIResponse(ResponseCode.SUCCESS, "查询成功", list);
    }

    // ============================= 下单接口 =============================

    /**
     * 创建订单
     * 通过 Feign 调用商品服务校验库存并扣减
     */
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @Transactional
    public APIResponse create(Order order) throws IOException {
        // 1. 通过 Feign 获取商品信息（校验库存）
        Thing thing;
        try {
            thing = thingFeignClient.getThingById(order.getThingId());
        } catch (Exception e) {
            logger.error("获取商品信息失败: {}", e.getMessage());
            return new APIResponse(ResponseCode.FAIL, "获取商品信息失败");
        }

        if (thing == null) {
            return new APIResponse(ResponseCode.FAIL, "商品不存在");
        }

        int count = Integer.parseInt(order.getCount());
        if (count > thing.getRepertory()) {
            return new APIResponse(ResponseCode.FAIL, "库存不足");
        }

        // 2. 通过 Feign 扣减库存
        try {
            APIResponse stockResp = thingFeignClient.deductStock(order.getThingId(), count);
            if (stockResp.getCode() != ResponseCode.SUCCESS.getCode()) {
                return new APIResponse(ResponseCode.FAIL, "扣减库存失败");
            }
        } catch (Exception e) {
            logger.error("扣减库存失败: {}", e.getMessage());
            return new APIResponse(ResponseCode.FAIL, "扣减库存失败");
        }

        // 3. 创建订单记录
        service.createOrder(order);
        return new APIResponse(ResponseCode.SUCCESS, "创建成功");
    }

    // ============================= 管理接口 =============================

    /**
     * 管理员：删除订单
     */
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public APIResponse delete(String ids) {
        String[] arr = ids.split(",");
        for (String id : arr) {
            service.deleteOrder(id);
        }
        return new APIResponse(ResponseCode.SUCCESS, "删除成功");
    }

    /**
     * 更新订单
     */
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @Transactional
    public APIResponse update(Order order) throws IOException {
        service.updateOrder(order);
        return new APIResponse(ResponseCode.SUCCESS, "更新成功");
    }

    /**
     * 管理员：取消订单
     */
    @RequestMapping(value = "/cancelOrder", method = RequestMethod.POST)
    @Transactional
    public APIResponse cancelOrder(Long id) throws IOException {
        Order order = new Order();
        order.setId(id);
        order.setStatus("7"); // 7=取消
        service.updateOrder(order);
        return new APIResponse(ResponseCode.SUCCESS, "取消成功");
    }

    /**
     * 用户：取消自己的订单
     */
    @RequestMapping(value = "/cancelUserOrder", method = RequestMethod.POST)
    @Transactional
    public APIResponse cancelUserOrder(Long id) throws IOException {
        Order order = new Order();
        order.setId(id);
        order.setStatus("7"); // 7=取消
        service.updateOrder(order);
        return new APIResponse(ResponseCode.SUCCESS, "取消成功");
    }
}
