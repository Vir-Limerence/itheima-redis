package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.entity.RedisData;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置过期时间
     * @param key redis中的key
     * @param value redis中的key
     * @param time 过期时间
     * @param unit 过期时间的单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间
     * @param key redis中的key
     * @param value redis中的key
     * @param time 过期时间
     * @param unit 过期时间的单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        //1.设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setObject(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //2.写入redis数据库
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透：缓存空对象
     * 解决缓存雪崩：为TTL添加随机值
     * @param id
     * @return {@link Shop }
     */
    public <R, ID> R queryWithPassThrough(
            String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit){
        //1.从redis从查询商铺信息缓存
        String key = prefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(jsonStr)){
            //3.存在，直接返回
            return JSONUtil.toBean(jsonStr, type);
        }
        //判断如果是空字符串
        if (jsonStr!=null){
            //返回错误信息
            return null;
        }
        //4.不存在，查询数据库
        R r = dbCallBack.apply(id);
        //5.判断是否查到
        if(r==null){
            this.set(key, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.如果存在，将数据写入redis缓存
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 解决缓存击穿：互斥锁
     * @param id
     * @return {@link Shop }
     */
    public<R,ID> R queryWithMutex(String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time , TimeUnit unit){
        //1.从redis从查询商铺信息缓存
        String key = prefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(jsonStr)){
            //3.存在，直接返回
            return JSONUtil.toBean(jsonStr, type);
        }
        //4.判断如果是空字符串
        if (jsonStr!=null){
            //5.返回错误信息
            return null;
        }
        R r = null;
        try {
            //6.获取互斥锁
            boolean isLock = tryLock(id);
            //7.没有获取到互斥锁
            if(!isLock){
                Thread.sleep(RedisConstants.SLEEP_GET_SHOP);
                return queryWithMutex(prefix, id, type, dbCallBack, time, unit);
            }
            //获取互斥锁成功后，应当再次检测redis缓存是否存在，做DoubleCheck，如果存在则无需重建缓存
            jsonStr = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(jsonStr)){
                return JSONUtil.toBean(jsonStr, type);
            }
            //8.成功获取到互斥锁，查询数据库重建缓存
            r = dbCallBack.apply(id);
            //9.判断是否查到
            if(r==null){
                stringRedisTemplate.opsForValue().set(key, "");
                stringRedisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //10.如果存在，将数据写入redis缓存
            jsonStr = JSONUtil.toJsonStr(r);
            stringRedisTemplate.opsForValue().set(key, jsonStr);
            stringRedisTemplate.expire(key, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //11.最后，释放互斥锁
            unlock(id);
        }
        return r;
    }


    /**
     * 解决缓存击穿：逻辑锁
     * @param id
     * @return {@link Shop }
     */
    public<R,ID> R queryWithLogicExpire(String prefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit unit) {
        //1.从redis从查询商铺信息缓存
        String key = prefix + id;
        String redisJson = stringRedisTemplate.opsForValue().get(key);
        //2.如果没有查询到信息则直接返回
        if (StrUtil.isBlank(redisJson)){
            //直接返回
            return null;
        }
        //4.在缓存中查询到了，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(redisJson, RedisData.class);
        JSONObject jsonStr = (JSONObject) redisData.getObject();
        R r = JSONUtil.toBean(jsonStr, type);
        //5.判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //6.如果没有过期
            return r;
        }
        //7.如果过期了，尝试获取互斥锁
        boolean isLock = tryLock(id);
        if (isLock) {
            //TODO: 2024/5/16 获取互斥锁成功时应该再次检测redis缓存是否过期，做DoubleCheck。如果存在则无需重建缓存
            redisJson = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(redisJson, RedisData.class);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
                return JSONUtil.toBean((JSONObject) redisData.getObject(), type);
            }
            //8.如果成功获取互斥锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbCallBack.apply(id);
                    //为缓存重建过程手动添加延时
                    Thread.sleep(200);
                    this.setWithLogicExpire(key, r1, time, unit);
                } catch (RuntimeException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(id);
                }
            });
        }
        //9.返回过期的商铺信息
        return r;
    }

    /**
     * 尝试获取互斥锁
     * @param id
     * @return {@link Boolean }
     */
    private<ID> boolean tryLock(ID id){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, RedisConstants.LOCK_SHOP_VALUE,
                RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //避免拆箱出现的空指针问题
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param id
     */
    private<ID> void unlock(ID id){
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY+id);
    }


}
