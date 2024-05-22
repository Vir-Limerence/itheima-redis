package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.BlogServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
@Api(tags = "笔记接口管理")
public class BlogController {

    @Resource
    private IBlogService blogService;


    /**
     * 新增笔记
     * @param blog
     * @return {@link Result }
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 根据id给笔记点赞
     * @param id
     * @return {@link Result }
     */
    @PutMapping("/like/{id}")
    @ApiOperation("根据id给笔记点赞")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 分页查询用户笔记
     * @param current
     * @return {@link Result }
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 查询某页的热点笔记
     * @param current
     * @return {@link Result }
     */
    @GetMapping("/hot")
    @ApiOperation("查询某页的热点笔记")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 根据id获取笔记信息
     * @param id
     * @return {@link Result }
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id获取笔记信息")
    public Result getBlog(@PathVariable Long id){
        return blogService.queryBlogById(id);
    }

    /**
     * 根据id查询点赞列表
     * @param id
     * @return {@link Result }
     */
    @GetMapping("/likes/{id}")
    @ApiOperation("根据id查询点赞列表")
    public Result queryBlogLikes(@PathVariable Long id){
        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据id分页查询用户笔记
     * @return {@link Result }
     */
    @GetMapping("/of/user")
    @ApiOperation("根据id分页查询用户笔记")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id){
        return blogService.queryBlogByUserId(current, id);
    }

    /**
     * 查询收件箱中的笔记
     * @param max
     * @param offset
     * @return {@link Result }
     */
    @GetMapping("/of/follow")
    public Result queryBlogOffFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0")Integer offset){
        return blogService.queryBlogOffFollow(max, offset);
    }
}
