package com.freshmall.order.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Cart;
import com.freshmall.order.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public APIResponse list(String userId) {
        try {
            List<Cart> list = cartService.getUserCartList(userId);
            return new APIResponse(ResponseCode.SUCCESS, "查询成功", list);
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        }
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public APIResponse add(String userId, String thingId, Integer count) {
        try {
            cartService.addCart(userId, thingId, count);
            return new APIResponse(ResponseCode.SUCCESS, "加入购物车成功");
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        }
    }

    @RequestMapping(value = "/updateCount", method = RequestMethod.POST)
    public APIResponse updateCount(String userId, Long id, Integer count) {
        try {
            cartService.updateCartCount(userId, id, count);
            return new APIResponse(ResponseCode.SUCCESS, "更新成功");
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        }
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public APIResponse delete(String userId, Long id) {
        try {
            cartService.deleteCart(userId, id);
            return new APIResponse(ResponseCode.SUCCESS, "删除成功");
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        }
    }

    @RequestMapping(value = "/checkout", method = RequestMethod.POST)
    public APIResponse checkout(String userId, String ids, String receiverName, String receiverPhone,
            String receiverAddress, String remark) {
        try {
            Map<String, Object> data = cartService.checkout(userId, ids, receiverName, receiverPhone, receiverAddress,
                    remark);
            APIResponse response = new APIResponse(ResponseCode.SUCCESS, "结算成功");
            response.setData(data);
            return response;
        } catch (IllegalArgumentException e) {
            return new APIResponse(ResponseCode.FAIL, e.getMessage());
        }
    }
}
