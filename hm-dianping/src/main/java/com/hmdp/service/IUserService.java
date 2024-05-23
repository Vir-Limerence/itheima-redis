package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return {@link Result }
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return {@link Result }
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 根据id查询用户信息
     * @param id
     * @return {@link Result }
     */
    Result queryUserById(Long id);


    /**
     * 登出功能
     * @param token
     * @return {@link Result }
     */
    Result logout(String token);

    /**
     * 用户签到
     * @return {@link Result }
     */
    Result sign();

    /**
     * 统计用户当月连续签到天数
     * @return {@link Result }
     */
    Result signCount();
}
