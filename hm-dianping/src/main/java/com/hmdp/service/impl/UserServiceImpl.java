package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return {@link Result }
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断手机号是否有效
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.无效，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.生成验证码，随机的6位数
        String code = RandomUtil.randomString(6);
        //4.保存验证码到redis
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码给用户
        log.info("验证码为：{}", code);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return {@link Result }
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
        //2.redis获取验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(code==null || !code.equals(loginForm.getCode())){
            return Result.fail("验证码错误！");
        }
        //3.根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //4.如果不存在则创建新用户，并保存
        if(user==null){
            user = createUserWithPhone(phone);
        }
        //5.将用户信息保存到redis
        //5.1 生成随机token
        String token = UUID.randomUUID().toString(true);
        //5.2利用hutool创建userDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //5.3将userDTO转换成MAP
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, filedValue) -> filedValue==null?null:filedValue.toString()));
        String key = RedisConstants.LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.将token返回给客户端
        return Result.ok(token);
    }

    /**
     * 根据id查询用户信息
     * @param id
     * @return {@link Result }
     */
    @Override
    public Result queryUserById(Long id) {
        User user = getById(id);
        if(user==null){
            return Result.fail("无法查询到用户信息！");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }


    private User createUserWithPhone(String phone) {
        String nickName = "user_"+RandomUtil.randomString(10);
        User user = User.builder()
                .createTime(LocalDateTime.now())
                .phone(phone)
                .nickName(nickName)
                .build();
        //存储到数据库
        save(user);
        return user;
    }


    /**
     * 登出功能
     * @return {@link Result }
     */
    @Override
    public Result logout(String token) {
        //从redis中删除token
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Boolean isSuccess = stringRedisTemplate.delete(key);
        if(!isSuccess){
            return Result.fail("登出失败！");
        }
        return Result.ok();
    }
}
