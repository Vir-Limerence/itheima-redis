package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询某页的热点笔记
     * @param current
     * @return {@link Result }
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id获取笔记信息
     * @param id
     * @return {@link Result }
     */
    Result queryBlogById(Long id);

    /**
     * 根据id给笔记点赞
     * @param id
     * @return {@link Result }
     */
    Result likeBlog(Long id);

    /**
     * 根据id查询点赞列表
     * @param id
     * @return {@link Result }
     */
    Result queryBlogLikes(Long id);

    /**
     * 根据id分页查询用户笔记
     * @param current
     * @param id
     * @return {@link Result }
     */
    Result queryBlogByUserId(Integer current, Long id);

    /**
     * 新增笔记
     * @param blog
     * @return {@link Result }
     */
    Result saveBlog(Blog blog);

    /**
     * 分页查询用户笔记
     * @param current
     * @return {@link Result }
     */
    Result queryMyBlog(Integer current);

    /**
     * 查询收件箱中的笔记
     * @param max
     * @param offset
     * @return {@link Result }
     */
    Result queryBlogOffFollow(Long max, Integer offset);
}
