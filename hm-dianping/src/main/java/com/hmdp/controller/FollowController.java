package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
@Api(tags = "关注接口管理")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     * 操作用户关注
     * @param id
     * @param isFollow
     * @return {@link Result }
     */
    @PutMapping("/{id}/{isFollow}")
    @ApiOperation("操作用户关注")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow){
        return followService.follow(id , isFollow);
    }

    /**
     * 判断是否关注
     * @param id
     * @return {@link Result }
     */
    @GetMapping("/or/not/{id}")
    @ApiOperation("判断是否关注")
    public Result isFollow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    /**
     * 根据id查询共同关注
     * @param id
     * @return {@link Result }
     */
    @GetMapping("/common/{id}")
    @ApiOperation("根据id查询共同关注")
    public Result followCommons(@PathVariable Long id){
        return followService.followCommons(id);
    }


}
