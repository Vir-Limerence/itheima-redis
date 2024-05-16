package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 根据id查询商铺信息
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryById(Long id) {
        //1.从redis从查询商铺信息缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(shopJson!=null){
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //4.不存在，查询数据库
        Shop shop = getById(id);
        //5.判断是否查到
        if(shop==null){
            return Result.fail("店铺不存在！");
        }
        //6.如果存在，将数据写入redis缓存
        shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, shopJson);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopJson);
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
}
