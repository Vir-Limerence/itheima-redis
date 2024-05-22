package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    /**
     * 操作用户关注
     * @param id
     * @param isFollow
     * @return {@link Result }
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取登录对象
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;
        //1.判断是关注还是取关
        if(isFollow){
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                //把关注用户id，放入redis的set集合 sadd key id
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        }else{
            //3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id", id));
            //把关注用户id，从redis的set集合移除
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }

        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result isFollow(Long id) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 查询是否关注 select count(id) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", id).count();
        return Result.ok(count>0);
    }

    /**
     * 根据id查询共同关注
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;
        //2.求交集
        String key2 = RedisConstants.FOLLOWS_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect==null || intersect.isEmpty()){
            return Result.ok();
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
