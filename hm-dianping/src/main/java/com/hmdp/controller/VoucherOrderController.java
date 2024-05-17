package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
@Api(tags = "优惠券订单接口管理")
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    /**
     * 优惠券秒杀下单
     * @param voucherId
     * @return {@link Result }
     */
    @PostMapping("seckill/{id}")
    @ApiOperation("优惠券秒杀下单")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return voucherOrderService.seckillVoucher(voucherId);
    }
}
