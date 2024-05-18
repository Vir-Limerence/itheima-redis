package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
//    private static final String

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, ID_PREFIX+threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //1.获取当前锁中的线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //2.获取Redis中锁的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //3.判断是否一致，一致则可以释放锁
        if(threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }
}
