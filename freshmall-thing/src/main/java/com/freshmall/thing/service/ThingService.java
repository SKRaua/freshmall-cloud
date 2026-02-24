package com.freshmall.thing.service;

import com.freshmall.common.entity.Thing;

import java.util.List;

public interface ThingService {
    List<Thing> getThingList(String keyword, String sort, String c, String cc);

    void createThing(Thing thing);

    void deleteThing(String id);

    void updateThing(Thing thing);

    Thing getThingById(String id);

    void addWishCount(String thingId);

    void addCollectCount(String thingId);

    List<Thing> getUserThing(String userId);

    List<Thing> getThingListByThingIds(List<Long> thingIdList);

    List<Thing> getDefaultThingList();

    /**
     * 获取商品详情（不计PV，供服务间调用）
     */
    Thing getThingByIdSimple(String id);

    /**
     * 扣减库存
     * 
     * @return true=成功, false=库存不足
     */
    boolean deductStock(String thingId, int count);
}
