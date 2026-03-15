package com.freshmall.order.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Order;
import com.freshmall.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    OrderService service;

    // ============================= 查询接口 =============================

    /**
     * 管理员：查询全部订单列表
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public APIResponse list(String orderNumber, String username, String status, String startTime, String endTime) {
        List<Order> list = service.getOrderList(orderNumber, username, status, startTime, endTime);
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
    public APIResponse create(Order order) throws IOException {
        try {
            Order created = service.createOrder(order);
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", created.getId());
            data.put("orderNumber", created.getOrderNumber());
            APIResponse response = new APIResponse(ResponseCode.SUCCESS, "创建成功");
            response.setData(data);
            return response;
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        } catch (Exception e) {
            logger.error("创建订单失败", e);
            return new APIResponse(ResponseCode.FAIL, "创建失败");
        }
    }

    // ============================= 管理接口 =============================

    /**
     * 管理员：删除订单
     */
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public APIResponse delete(String ids, HttpServletRequest request) {
        String adminToken = request.getHeader("ADMINTOKEN");
        if (!StringUtils.hasText(adminToken)) {
            return new APIResponse(ResponseCode.FAIL, "无权限删除订单");
        }

        if (!StringUtils.hasText(ids)) {
            return new APIResponse(ResponseCode.FAIL, "订单ID不能为空");
        }

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
    public APIResponse update(Order order) throws IOException {
        service.updateOrder(order);
        return new APIResponse(ResponseCode.SUCCESS, "更新成功");
    }

    /**
     * 管理员：取消订单
     */
    @RequestMapping(value = "/cancelOrder", method = RequestMethod.POST)
    public APIResponse cancelOrder(Long id) throws IOException {
        boolean success = service.cancelOrderByAdmin(id);
        if (!success) {
            return new APIResponse(ResponseCode.FAIL, "取消失败");
        }
        return new APIResponse(ResponseCode.SUCCESS, "取消成功");
    }

    /**
     * 用户：取消自己的订单
     */
    @RequestMapping(value = "/cancelUserOrder", method = RequestMethod.POST)
    public APIResponse cancelUserOrder(Long id, String userId) throws IOException {
        boolean success = service.cancelOrderByUser(id, userId);
        if (!success) {
            return new APIResponse(ResponseCode.FAIL, "取消失败");
        }
        return new APIResponse(ResponseCode.SUCCESS, "取消成功");

    }
}
