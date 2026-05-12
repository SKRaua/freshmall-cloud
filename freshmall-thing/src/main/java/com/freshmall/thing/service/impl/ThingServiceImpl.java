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

    // 使用内嵌的 Lua 脚本保证 Redis 预扣减库存的原子性
    // Lua 脚本执行逻辑：“获取锁(KEYS) → 检查库存(ARGV判断) → 扣减库存 → 释放锁(隐式返回)”
    // Redis 采用单线程执行 Lua 脚本，执行期间不会被其他命令插队，从根本上防止高并发超卖。
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
        self.refreshThingCache(buildCacheThing(thing));
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
        return buildCacheThing(thing);
    }

    private Thing buildCacheThing(Thing thing) {
        if (thing == null) {
            return null;
        }

        // 仅缓存可序列化且业务需要的字段，特别保留 cover（图片编号/路径）。
        Thing cacheThing = new Thing();
        cacheThing.setId(thing.getId());
        cacheThing.setTitle(thing.getTitle());
        cacheThing.setCover(thing.getCover());
        cacheThing.setDescription(thing.getDescription());
        cacheThing.setPrice(thing.getPrice());
        cacheThing.setStatus(thing.getStatus());
        cacheThing.setCreateTime(thing.getCreateTime());
        cacheThing.setScore(thing.getScore());
        cacheThing.setPinzhong(thing.getPinzhong());
        cacheThing.setBaozhiqi(thing.getBaozhiqi());
        cacheThing.setShengchanriqi(thing.getShengchanriqi());
        cacheThing.setRepertory(thing.getRepertory());
        cacheThing.setPv(thing.getPv());
        cacheThing.setRate(thing.getRate());
        cacheThing.setRecommendCount(thing.getRecommendCount());
        cacheThing.setWishCount(thing.getWishCount());
        cacheThing.setCollectCount(thing.getCollectCount());
        cacheThing.setClassificationId(thing.getClassificationId());
        cacheThing.setTags(thing.getTags());
        cacheThing.setUserId(thing.getUserId());

        // MultipartFile 由 Servlet 容器实现，不可序列化，不能进入 Redis 缓存值。
        cacheThing.setImageFile(null);
        return cacheThing;
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
        // 原生 execute 调用，将 key 和扣减数量传入上面的 Lua 脚本中执行
        Long result = stringRedisTemplate.execute(RESERVE_STOCK_SCRIPT, Collections.singletonList(key),
                String.valueOf(count));
        return result != null && result >= 0;
    }

    @Override
    // 最终扣减库存（MQ 消费者调用）：在订单创建成功后，负责将 DB 里的实体库存彻底扣除。
    // 同时清理商品详情缓存，确保前台页面展示最新库存。
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public boolean confirmDeductStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        // 执行 DB 层面的行锁扣减
        boolean success = mapper.deductStock(thingId, count) > 0;
        if (!success && stringRedisTemplate != null) {
            // 极限补偿机制：如果缓存前面预占成功了，但落库时由于某些极端原因（如手工改库导致库存成了负数）被 DB 拒绝，
            // 此时必须把 Redis 里被吃掉的额度还回来，防止数据割裂。
            stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        }
        return success;
    }

    @Override
    // 释放并恢复库存（MQ 消费者调用 - 对应取消订单）：当订单被主动取消或超时未支付时触发。
    // 需要双管齐下：既要归还 Redis 中预扣的共享库存，也要增加 DB 里的实际库存。
    @CacheEvict(key = "#thingId", condition = "#thingId != null && !#thingId.trim().isEmpty()")
    public boolean releaseStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        if (stringRedisTemplate != null) {
            initStockCacheIfAbsent(thingId);
            // 归还 Redis 预扣缓存量
            stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        }
        // 归还底层 DB 实体库存
        return mapper.increaseStock(thingId, count) > 0;
    }

    @Override
    // 纯缓存回流（订单服务 RPC 同步调用）：专用于订单创建“主事务中途发生异常（例如扣款失败或 Outbox 存储失败）”。
    // 仅仅因为预扣成功了，但并没有进行任何 DB 扣减，所以只需单独释放 Redis 里的缓存占有量，无需碰实体的 DB 库存。
    public boolean unreserveStock(String thingId, int count) {
        if (count <= 0 || thingId == null || thingId.trim().isEmpty()) {
            return false;
        }
        if (stringRedisTemplate == null) {
            return true;
        }
        initStockCacheIfAbsent(thingId);
        // 使用原子递增归还 Redis 容量
        Long value = stringRedisTemplate.opsForValue().increment(buildStockKey(thingId), count);
        return value != null;
    }

    // 缓存懒加载兜底机制：一旦 Redis 里的库存缓存由于过期或重启而丢失，
    // 在下一次请求时安全地通过查询 DB 将当前可用真实库存重载回 Redis。
    private void initStockCacheIfAbsent(String thingId) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = buildStockKey(thingId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            // 已存在，不用预热
            return;
        }
        Thing thing = mapper.selectById(thingId);
        if (thing == null) {
            return;
        }
        // 使用 setIfAbsent (SETNX) 确保并发下只有一个线程可以成功重建缓存
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
