package com.freshmall.thing.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.ThingWish;
import com.freshmall.thing.service.ThingService;
import com.freshmall.thing.service.ThingWishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/thingWish")
public class ThingWishController {

    private final static Logger logger = LoggerFactory.getLogger(ThingWishController.class);

    @Autowired
    ThingWishService thingWishService;

    @Autowired
    ThingService thingService;

    @RequestMapping(value = "/wish", method = RequestMethod.POST)
    @Transactional
    public APIResponse wish(ThingWish thingWish) throws IOException {
        if (thingWishService.getThingWish(thingWish.getUserId(), thingWish.getThingId()) != null) {
            return new APIResponse(ResponseCode.SUCCESS, "您已添加过了");
        } else {
            thingWishService.createThingWish(thingWish);
            thingService.addWishCount(thingWish.getThingId());
        }
        return new APIResponse(ResponseCode.SUCCESS, "添加成功");
    }

    @RequestMapping(value = "/unWish", method = RequestMethod.POST)
    @Transactional
    public APIResponse unWish(String id) throws IOException {
        thingWishService.deleteThingWish(id);
        return new APIResponse(ResponseCode.SUCCESS, "取消收藏成功");
    }

    @RequestMapping(value = "/getUserWishList", method = RequestMethod.GET)
    @Transactional
    public APIResponse getUserWishList(String userId) throws IOException {
        List<Map> lists = thingWishService.getThingWishList(userId);
        return new APIResponse(ResponseCode.SUCCESS, "获取成功", lists);
    }
}
