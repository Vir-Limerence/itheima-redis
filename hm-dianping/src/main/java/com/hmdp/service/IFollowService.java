package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 操作用户关注
     * @param id
     * @param isFollow
     * @return {@link Result }
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 判断是否关注
     * @param id
     * @return {@link Result }
     */
    Result isFollow(Long id);

    /**
     * 根据id查询共同关注
     * @param id
     * @return {@link Result }
     */
    Result followCommons(Long id);
}
