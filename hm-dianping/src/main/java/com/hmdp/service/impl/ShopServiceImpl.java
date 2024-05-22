package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryById(Long id) {
        //1.解决缓存穿透:缓存空空对象
        Shop shop = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //2.解决缓存雪崩：为TTL添加随机值
        //3.解决缓存击穿
        //3.1互斥锁解决缓存击穿问题：添加互斥锁
//        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //3.2逻辑过期解决缓存击穿问题:添加逻辑过期时间
//        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     * @param shop
     * @return {@link Result }
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        //1.获取店铺id
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺不存在！");
        }
        //2.写入数据库
        updateById(shop);
        //3.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if(x==null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }
        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3. 查询redis，按照距离排序 分页 结果：shopId distance
        // GEOSEARCH BYLONLAT x y BYRADIUS 10 WITHINSTANCE
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        // 4. 解析出id
        if(results == null){
            return Result.ok("5km距离范围内没有饭店！");
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if(list.size()<=from){
            log.info("{},{}", list().size(), from);
            return Result.ok("");
        }

        // 4.1截取从from到end
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>();
        list.stream().skip(from).collect(Collectors.toList()).forEach(result ->{
            // 4.2 获取店铺id
            String shopIdStr = result.getContent().getName();

            ids.add(Long.valueOf(shopIdStr));
            // 4.3 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5. 根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 6. 为店铺添加距离
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回数据
        return Result.ok(shops);
    }

    /**
     * 根据id查询Shop对象，转换为RedisData对象存储在Redis缓存中
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询对应的店铺信息
        Shop shop = getById(id);
        //为缓存重建过程手动添加延时
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setObject(shop);
        //3.将数据写入redis，不再添加缓存时间
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,
                JSONUtil.toJsonStr(redisData));
    }
}
