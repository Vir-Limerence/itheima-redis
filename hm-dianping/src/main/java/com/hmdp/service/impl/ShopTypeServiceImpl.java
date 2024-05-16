package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 获取商铺类型列表
     * @return {@link Result }
     */
    @Override
    public Result queryTypeList() {
        //1.从redis缓存中获取商铺类型数据
        List<String> typeJsonList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.判断是否为空
        if(!typeJsonList.isEmpty()){
            //3.不为空，将查询到的数据进行转换后返回
            List<ShopType> typeList = typeJsonList.stream().map(str -> JSONUtil.toBean(str, ShopType.class)).collect(Collectors.toList());
            return Result.ok(typeList);
        }
        //3.如果为空，则查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4.如果数据库为空，返回错误
        if(typeList==null || typeList.isEmpty()){
            return Result.fail("商铺类型数据不存在！");
        }
        //5.将查询到的数据存入Redis数据库
        typeJsonList = typeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, typeJsonList);

        return Result.ok(typeList);
    }
}
