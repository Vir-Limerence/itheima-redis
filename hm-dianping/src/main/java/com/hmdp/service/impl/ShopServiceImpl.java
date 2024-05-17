package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
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
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //2.解决缓存雪崩：为TTL添加随机值
        //3.解决缓存击穿
        //3.1互斥锁解决缓存击穿问题：添加互斥锁
//        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //3.2逻辑过期解决缓存击穿问题:添加逻辑过期时间
        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
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
