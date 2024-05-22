package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(record->{
            this.queryBlogUser(record);
            this.isBlogLiked(record);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在！");
        }
        //2.查询笔记相关的用户
        queryBlogUser(blog);
        //3.查询是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        log.info("当前用户为:{}", user);
        if(user == null){
            //如果没有用户登录，直接返回
            return;
        }
        Long userId = user.getId();
        //2.判断当前用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 根据id给笔记点赞
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result likeBlog(Long id) {
        //1.判断用户是否给笔记点过赞
        //1.1 获取key
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //1.2获取用户id
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null){
            return Result.ok();
        }
        Long userId = userDTO.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score==null){
            //3.说明用户没有点赞
            //3.1 数据库点赞 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存点赞成功的用户到Zset集合
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //4.如果已经点赞，这里取消点赞
            //4.1 数据库点赞 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2 删除取消点赞的用户
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }

        }
        return Result.ok();
    }

    /**
     * 根据id查询点赞列表
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top 5的点赞用户
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析用户id 查询用户 返回
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOList = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id,"+idStr+")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    /**
     * 根据id分页查询用户笔记
     * @param current
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
}
