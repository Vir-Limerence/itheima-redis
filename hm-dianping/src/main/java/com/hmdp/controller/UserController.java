package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Api(tags = "用户接口管理")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    @ApiOperation("发送手机验证码")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    @ApiOperation("登录功能")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){

        return userService.login(loginForm, session);
    }


    /**
     * 登出功能
     * @param request
     * @return {@link Result }
     */
    @PostMapping("/logout")
    @ApiOperation("登出功能")
    public Result logout(HttpServletRequest request){
        String token = request.getHeader("authorization");
        return userService.logout(token);
    }

    /**
     * 个人主页信息
     * @return {@link Result }
     */
    @GetMapping("/me")
    @ApiOperation("个人主页信息")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 查询用户详情
     * @param userId
     * @return {@link Result }
     */
    @GetMapping("/info/{id}")
    @ApiOperation("查询用户详情")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 根据id查询用户信息
     * @param id
     * @return {@link Result }
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询用户信息")
    public Result queryUserById(@PathVariable Long id){
        return userService.queryUserById(id);
    }

    /**
     * 用户签到
     * @return {@link Result }
     */
    @PostMapping("/sign")
    @ApiOperation("用户签到")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 统计用户当月连续签到天数
     * @return {@link Result }
     */
    @GetMapping("/sign/count")
    @ApiOperation("统计用户当月连续签到天数")
    public Result signCount(){
        return userService.signCount();
    }

}
