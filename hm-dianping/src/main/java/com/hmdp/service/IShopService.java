package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return {@link Result }
     */
    Result queryById(Long id);


    /**
     * 更新店铺信息
     * @param shop
     * @return {@link Result }
     */
    Result updateShop(Shop shop);

    /**
     * 根据商铺类型和地理坐标分页查询商铺id
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return {@link Result }
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
