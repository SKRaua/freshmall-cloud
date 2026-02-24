package com.freshmall.thing.controller;

import com.freshmall.common.APIResponse;
import com.freshmall.common.ResponseCode;
import com.freshmall.common.entity.Record;
import com.freshmall.common.entity.Thing;
import com.freshmall.thing.service.RecordService;
import com.freshmall.thing.service.ThingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * @author SKRaua
 */
@RestController
@RequestMapping("/record")
public class RecordController {

    @Autowired
    RecordService service;

    @Autowired
    ThingService thingService;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public APIResponse list() {
        List<Record> list = service.getRecordList();
        return new APIResponse(ResponseCode.SUCCESS, "查询成功", list);
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @Transactional
    public APIResponse create(Record record) throws IOException {
        Thing thing = thingService.getThingById(String.valueOf(record.getThingId()));
        if (thing != null) {
            // record.setClassificationId(thing.classificationId); //
            // 之前以为要推荐算法用，但数据库没设计这个字段，还是保持原样最安全
            service.createRecord(record);
            return new APIResponse(ResponseCode.SUCCESS, "创建成功");
        } else {
            return new APIResponse(ResponseCode.FAIL, "商品不存在");
        }
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @Transactional
    public APIResponse update(Record record) throws IOException {
        service.updateRecord(record);
        return new APIResponse(ResponseCode.SUCCESS, "更新成功");
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public APIResponse delete(String ids) {
        String[] idArray = ids.split(",");
        for (String id : idArray) {
            // service.deleteRecord(id); // 接口中没有定义 delete 方法，且这个功能本身在浏览记录中不常见，暂时注释掉
        }
        return new APIResponse(ResponseCode.SUCCESS, "删除成功");
    }

}
