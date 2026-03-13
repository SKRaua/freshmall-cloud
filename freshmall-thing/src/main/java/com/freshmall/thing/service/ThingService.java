package com.freshmall.thing.service;

import com.freshmall.common.entity.Thing;

import java.util.List;

public interface ThingService {
    /**
     * 商品列表查询（支持关键字、排序、分类筛选）。
     */
    List<Thing> getThingList(String keyword, String sort, String c, String cc);

    /**
     * 新增商品。
     */
    void createThing(Thing thing);

    /**
     * 删除商品。
     */
    void deleteThing(String id);

    /**
     * 更新商品基础信息。
     */
    void updateThing(Thing thing);

    /**
     * 前台商品详情查询（会累计 PV，并刷新详情缓存）。
     */
    Thing getThingById(String id);

    /**
     * 商品点赞数 +1。
     */
    void addWishCount(String thingId);

    /**
     * 商品收藏数 +1。
     */
    void addCollectCount(String thingId);

    /**
     * 按商品 ID 集合批量查询（推荐场景使用）。
     */
    List<Thing> getThingListByThingIds(List<Long> thingIdList);

    /**
     * 推荐降级列表（按热度/PV倒序）。
     */
    List<Thing> getDefaultThingList();

    /**
     * 获取商品详情（不计 PV，适合服务间调用/存在性校验）。
     */
    Thing getThingByIdSimple(String id);

    /**
     * 刷新商品详情缓存（供服务内部通过代理调用）。
     */
    Thing refreshThingCache(Thing thing);

    /**
     * 扣减库存。
     * 
     * @return true=成功，false=库存不足
     */
    boolean deductStock(String thingId, int count);
}
