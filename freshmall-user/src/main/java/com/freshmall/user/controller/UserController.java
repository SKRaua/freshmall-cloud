package com.freshmall.user.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.User;
import com.freshmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 用户微服务Controller
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserService service;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public APIResponse list(String keyword) {
        List<User> list = service.getUserList(keyword);
        return new APIResponse(ResponseCode.SUCCESS, "查询成功", list);
    }

    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    public APIResponse detail(User user) {
        User dbUser = service.getUserDetail(user.getId());
        return new APIResponse(ResponseCode.SUCCESS, "查询成功", dbUser);
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public APIResponse create(User user) throws IOException {
        service.createUser(user);
        return new APIResponse(ResponseCode.SUCCESS, "创建成功");
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public APIResponse update(User user) throws IOException {
        service.updateUser(user);
        return new APIResponse(ResponseCode.SUCCESS, "更新成功");
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public APIResponse delete(String ids) {
        String[] idArray = ids.split(",");
        for (String id : idArray) {
            service.deleteUser(id);
        }
        return new APIResponse(ResponseCode.SUCCESS, "删除成功");
    }

    @RequestMapping(value = "/userLogin", method = RequestMethod.POST)
    public APIResponse userLogin(User user) {
        User loginUser = service.getNormalUser(user);
        if (loginUser != null) {
            if (String.valueOf(User.AdminUser).equals(loginUser.getRole())) {
                return new APIResponse(ResponseCode.FAIL, "想进后台请走管理员通道");
            }
            String token = java.util.UUID.randomUUID().toString().replaceAll("-", "");
            loginUser.setToken(token);
            service.updateUser(loginUser);
            return new APIResponse(ResponseCode.SUCCESS, "登录成功", loginUser);
        } else {
            return new APIResponse(ResponseCode.FAIL, "账号或密码错误");
        }
    }

    @RequestMapping(value = "/adminLogin", method = RequestMethod.POST)
    public APIResponse adminLogin(User user) {
        User loginUser = service.getAdminUser(user);
        if (loginUser != null) {
            if (String.valueOf(User.NormalUser).equals(loginUser.getRole())) {
                return new APIResponse(ResponseCode.FAIL, "想进前台请走普通用户通道");
            }
            String token = java.util.UUID.randomUUID().toString().replaceAll("-", "");
            loginUser.setToken(token);
            service.updateUser(loginUser);
            return new APIResponse(ResponseCode.SUCCESS, "登录成功", loginUser);
        } else {
            return new APIResponse(ResponseCode.FAIL, "账号或密码错误");
        }
    }

}
