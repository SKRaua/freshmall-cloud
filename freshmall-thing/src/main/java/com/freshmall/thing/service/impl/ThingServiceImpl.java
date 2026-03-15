package com.freshmall.thing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.freshmall.common.entity.Thing;
import com.freshmall.thing.mapper.ThingMapper;
import com.freshmall.thing.service.ThingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
// 指定默认缓存名称，方法级注解可直接复用该缓存区域
@CacheConfig(cacheNames = "thingDetail")
public class ThingServiceImpl extends ServiceImpl<ThingMapper, Thing> implements ThingService {
    @Autowired
    ThingMapper mapper;

    @Autowired
    @Lazy
    // 通过代理调用自身带缓存注解的方法，避免 this.方法() 导致注解失效
    private ThingService self;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_KEY_PREFIX = "freshmall:stock:thing:";

    private static final DefaultRedisScript<Long> RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]);"
                    + "if (not v) then return -2 end;"
                    + "if (tonumber(v) < tonumber(ARGV[1])) then return -1 end;"
                    + "return redis.call('DECRBY', KEYS[1], ARGV[1]);",
            Long.class);

    @Override
    // 列表接口：用于首页/搜索页，支持关键字+排序+分类组合筛选。
    // 仅缓存“无关键字搜索”的列表（首页/分类页），避免把管理端检索列表放入缓存。
    @Cacheable(cacheNames = "thingList", key = "(#sort?:'')+'|'+(#c?:'')+'|'+(#cc?:'')", condition = "#keyword == null || #keyword.trim().isEmpty()", unless = "#result == null || #result.isEmpty()")
    public List<Thing> getThingList(String keyword, String sort, String c, String cc) {
        QueryWrapper<Thing> queryWrapper = new QueryWrapper<>();

        // 搜索
        queryWrapper.like(StringUtils.isNotBlank(keyword), "title", keyword);

        // 排序
        if (StringUtils.isNotBlank(sort)) {
            if (sort.equals("recent")) {
                queryWrapper.orderBy(true, false, "create_time");
            } else if (sort.equals("hot") || sort.equals("recommend")) {
                queryWrapper.orderBy(true, false, "pv");
            }
        } else {
            queryWrapper.orderBy(true, false, "create_time");
        }

        // 根据分类筛选
        if (StringUtils.isNotBlank(c) && !c.equals("-1")) {
            queryWrapper.eq(true, "classification_id", c);
        }

        // 根据分类2筛选
        if (StringUtils.isNotBlank(cc) && !cc.equals("全部")) {
            queryWrapper.eq(true, "location", cc);
        }

        List<Thing> things = mapper.selectList(queryWrapper);

        return things;
    }

    @Override
    // 新增商品时补齐默认字段，并预热详情缓存。
    @CacheEvict(cacheNames = "thingList", allEntries = true)
    public void createThing(Thing thing) {
        System.out.println(thing);
        thing.setCreateTime(String.valueOf(System.currentTimeMillis()));

        if (thing.getPv() == null) {
            thing.setPv("0");
        }
        if (thing.getScore() == null) {
            thing.setScore("0");
        }
        if (thing.getWishCount() == null) {
            thing.setWishCount("0");
        }
        mapper.insert(thing);
        // 预热详情缓存，减少新商品首次访问回源
        self.refreshThingCache(thing);
    }

    @Override
    // 删除商品后清理对应详情缓存，防止脏读
    @Caching(evict = {
            @CacheEvict(key = "#id", condition = "#id != null && !#id.trim().isEmpty()"),
            @CacheEvict(cacheNames = "thingList", allEntries = true)
    })
    public void deleteThing(String id) {
        mapper.deleteById(id);
    }

    @Override
    // 更新商品后清理详情缓存，下次读取自动回源并回填
    @Caching(evict = {
            @CacheEvict(key = "#thing.id", condition = "#thing != null && #thing.id != null"),
            @CacheEvict(cacheNames = "thingList", allEntries = true)
    })
    public void updateThing(Thing thing) {

        mapper.updateById(thing);
    }

    @Override
    // 前台详情查询：读取详情并累计 PV（与不计 PV 的 getThingByIdSimple 分工明确）。
    public Thing getThingById(String id) {
        // 注意：通过代理调用缓存方法，确保命中 @Cacheable。
        Thing thing = self.getThingByIdSimple(id);
        if (thing == null) {
            return null;
        }

        // PV 在 DB 中使用原子 SQL +1，避免并发读改写冲突
        mapper.increasePv(id);
        thing.setPv(String.valueOf(parseIntSafe(thing.getPv()) + 1));
        // 同步刷新缓存中的 PV，避免详情页持续看到旧值
        self.refreshThingCache(thing);

        return thing;
    }

    // 心愿数加1
    @Override
    // 点赞数变更会影响详情展示，清理对应缓存
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public void addWishCount(String thingId) {
        Thing thing = mapper.selectById(thingId);
        thing.setWishCount(String.valueOf(Integer.parseInt(thing.getWishCount()) + 1));
        mapper.updateById(thing);
    }

    // 收藏数加1
    @Override
    // 收藏数变更会影响详情展示，清理对应缓存
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public void addCollectCount(String thingId) {
        Thing thing = mapper.selectById(thingId);
        thing.setCollectCount(String.valueOf(Integer.parseInt(thing.getCollectCount()) + 1));
        mapper.updateById(thing);
    }

    @Override
    // 推荐模块批量回填商品详情。
    public List<Thing> getThingListByThingIds(List<Long> thingIdList) {
        QueryWrapper<Thing> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", thingIdList);
        return mapper.selectList(queryWrapper);
    }

    @Override
    // 推荐兜底：按 PV 倒序返回热门商品。
    public List<Thing> getDefaultThingList() {
        QueryWrapper<Thing> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("pv");
        return mapper.selectList(queryWrapper);
    }

    @Override
    // 详情读取入口：先查缓存，未命中才查询数据库
    @Cacheable(key = "#id", unless = "#result == null")
    public Thing getThingByIdSimple(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return mapper.selectById(id);
    }

    @Override
    // 使用 CachePut 将最新详情写回缓存；Redis 故障时由统一错误处理器兜底
    @CachePut(key = "#thing.id", condition = "#thing != null && #thing.id != null")
    public Thing refreshThingCache(Thing thing) {
        return thing;
    }

    @Override
    // 库存变更后清理缓存，避免详情页看到旧库存
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public boolean deductStock(String thingId, int count) {
        if (count <= 0) {
            return false;
        }

        return mapper.deductStock(thingId, count) > 0;
    }

    @Override
    public boolean reserveStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        if (stringRedisTemplate == null) {
            Thing thing = mapper.selectById(thingId);
            return thing != null && thing.getRepertory() >= count;
        }

        initStockCacheIfAbsent(thingId);
        String key = buildStockKey(thingId);
        Long result = stringRedisTemplate.execute(RESERVE_STOCK_SCRIPT, Collections.singletonList(key),
                String.valueOf(count));
        return result != null && result >= 0;
    }

    @Override
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public boolean confirmDeductStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        boolean success = mapper.deductStock(thingId, count) > 0;
        if (!success && stringRedisTemplate != null) {
            // 缓存已预占但 DB 扣减失败时，进行补偿回补。
            stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        }
        return success;
    }

    @Override
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public boolean releaseStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        if (stringRedisTemplate != null) {
            initStockCacheIfAbsent(thingId);
            stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        }
        return mapper.increaseStock(thingId, count) > 0;
    }

    @Override
    public boolean unreserveStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        if (stringRedisTemplate == null) {
            return true;
        }
        initStockCacheIfAbsent(thingId);
        Long value = stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        return value != null;
    }

    private void initStockCacheIfAbsent(String thingId) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = buildStockKey(thingId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        Thing thing = mapper.selectById(thingId);
        if (thing == null) {
            return;
        }
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(Math.max(thing.getRepertory(), 0)));
    }

    private String buildStockKey(String thingId) {
        return STOCK_KEY_PREFIX + thingId;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            // 兼容历史脏数据（null/空串/非数字），默认按 0 处理
            return 0;
        }
    }
}
