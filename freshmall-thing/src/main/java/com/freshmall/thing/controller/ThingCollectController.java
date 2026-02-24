package com.freshmall.thing.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.ThingCollect;
import com.freshmall.thing.service.ThingCollectService;
import com.freshmall.thing.service.ThingService;
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
@RequestMapping("/thingCollect")
public class ThingCollectController {

    private final static Logger logger = LoggerFactory.getLogger(ThingCollectController.class);

    @Autowired
    ThingCollectService thingCollectService;

    @Autowired
    ThingService thingService;

    @RequestMapping(value = "/collect", method = RequestMethod.POST)
    @Transactional
    public APIResponse collect(ThingCollect thingCollect) throws IOException {
        if (thingCollectService.getThingCollect(thingCollect.getUserId(), thingCollect.getThingId()) != null) {
            return new APIResponse(ResponseCode.SUCCESS, "您已收藏过了");
        } else {
            thingCollectService.createThingCollect(thingCollect);
            thingService.addCollectCount(thingCollect.getThingId());
        }
        return new APIResponse(ResponseCode.SUCCESS, "收藏成功");
    }

    @RequestMapping(value = "/unCollect", method = RequestMethod.POST)
    @Transactional
    public APIResponse unCollect(String id) throws IOException {
        thingCollectService.deleteThingCollect(id);
        return new APIResponse(ResponseCode.SUCCESS, "取消收藏成功");
    }

    @RequestMapping(value = "/getUserCollectList", method = RequestMethod.GET)
    @Transactional
    public APIResponse getUserCollectList(String userId) throws IOException {
        List<Map> lists = thingCollectService.getThingCollectList(userId);
        return new APIResponse(ResponseCode.SUCCESS, "获取成功", lists);
    }
}
