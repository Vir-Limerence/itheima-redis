package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;
    /**
     * 优惠券秒杀下单
     * @param voucherId
     * @return {@link Result }
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1.查询秒杀优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断是否开始
        if(voucher.getCreateTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀还未开始！");
        }
        //3.判断是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束！");
        }
        //4.判断库存
        if(voucher.getStock()<1){
            return Result.fail("库存不足！");
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if(!success){
            return Result.fail("库存不足！");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1优惠券id
        voucherOrder.setVoucherId(voucherId);
        //6.2用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //6.3订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        save(voucherOrder);
        //7.返回订单id
        return Result.ok(orderId);
    }
}
