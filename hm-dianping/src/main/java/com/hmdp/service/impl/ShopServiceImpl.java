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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryById(Long id) {
        //1.解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //2.解决缓存雪崩
        //Shop shop = queryWithSnowBreak(id);
        //3.解决缓存击穿
        //3.1互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);
        //3.2逻辑锁解决缓存击穿问题
        Shop shop = queryWithLogicExpire(id);
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
     * 解决缓存穿透：缓存空对象
     * @param id
     * @return {@link Shop }
     */
    private Shop queryWithPassThrough(Long id){
        //1.从redis从查询商铺信息缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断如果是空字符串
        if (shopJson!=null){
            //返回错误信息
            return null;
        }
        //4.不存在，查询数据库
        Shop shop = getById(id);
        //5.判断是否查到
        if(shop==null){
            stringRedisTemplate.opsForValue().set(key, "");
            stringRedisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.如果存在，将数据写入redis缓存
        shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, shopJson);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 解决缓存雪崩：为TTL添加随机值
     * @param id
     * @return {@link Shop }
     */
    private Shop queryWithSnowBreak(Long id){
        //1.从redis从查询商铺信息缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断如果是空字符串
        if (shopJson!=null){
            //返回错误信息
            return null;
        }
        //4.不存在，查询数据库
        Shop shop = getById(id);
        //5.判断是否查到
        if(shop==null){
            stringRedisTemplate.opsForValue().set(key, "");
            stringRedisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL + RandomUtil.randomLong(3), TimeUnit.MINUTES);
            return null;
        }
        //6.如果存在，将数据写入redis缓存
        shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, shopJson);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL+RandomUtil.randomLong(3), TimeUnit.MINUTES);
        return shop;
    }


    /**
     * 解决缓存击穿：互斥锁
     * @param id
     * @return {@link Shop }
     */
    private Shop queryWithMutex(Long id){
        //1.从redis从查询商铺信息缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //4.判断如果是空字符串
        if (shopJson!=null){
            //5.返回错误信息
            return null;
        }
        Shop shop = null;
        try {
            //6.获取互斥锁
            boolean isLock = tryLock(id);
            //7没有获取到互斥锁
            if(!isLock){
                Thread.sleep(20);
                return queryWithMutex(id);
            }
            //获取互斥锁成功后，应当再次检测redis缓存是否存在，做DoubleCheck，如果存在则无需重建缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJson)){
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //8.成功获取到互斥锁，查询数据库重建缓存
            shop = getById(id);
            //9.判断是否查到
            if(shop==null){
                stringRedisTemplate.opsForValue().set(key, "");
                stringRedisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //10.如果存在，将数据写入redis缓存
            shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson);
            stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //11.最后，释放互斥锁
            unlock(id);
        }
        return shop;
    }

    /**
     * 解决缓存击穿：逻辑锁
     * @param id
     * @return {@link Shop }
     */
    private Shop queryWithLogicExpire(Long id) {
        //1.从redis从查询商铺信息缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String redisJson = stringRedisTemplate.opsForValue().get(key);
        //2.如果没有查询到信息则直接返回
        if (StrUtil.isBlank(redisJson)){
            //直接返回
            return null;
        }
        //4.在缓存中查询到了，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(redisJson, RedisData.class);
        JSONObject shopJson = (JSONObject) redisData.getObject();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        //5.判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //6.如果没有过期
            return shop;
        }
        //7.如果过期了，尝试获取互斥锁
        boolean isLock = tryLock(id);
        if (isLock) {
            //TODO: 2024/5/16 获取互斥锁成功时应该再次检测redis缓存是否过期，做DoubleCheck。如果存在则无需重建缓存
            redisData = JSONUtil.toBean(redisJson, RedisData.class);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
                return JSONUtil.toBean((JSONObject) redisData.getObject(), Shop.class);
            }
            //8.如果成功获取互斥锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(id);
                }
            });
        }
        //9.返回过期的商铺信息
        return shop;
    }


    /**
     * 尝试获取互斥锁
     * @param id
     * @return {@link Boolean }
     */
    private boolean tryLock(Long id){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1",
                RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //避免拆箱出现的空指针问题
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(Long id){
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY+id);
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
