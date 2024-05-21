package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    private ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        //类初始化后执行
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
         @Override
        public void run(){
             while(true){
                 try {
                     log.info("开启异步下单线程！");
                     //1.获取队列中的订单信息
                     VoucherOrder voucherOrder = orderTasks.take();
                     //2.创建订单
                     handleVoucherOrder(voucherOrder);
                 } catch (Exception e) {
                     //日志记录异常
                     log.error("处理订单异常：", e);
                 }
             }
         }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
        RLock lock = redissonClient.getLock("lock:order:"+userId);
        //3.1尝试获取锁
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.error("不允许重复下单!");
        }
        //4.获取代理对象（事务）
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    /**
     * 优惠券秒杀下单
     * @param voucherId
     * @return {@link Result }
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        log.info("执行秒杀任务");
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        if(voucherId==null){
            return Result.fail("优惠券不存在！");
        }
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        int r = result.intValue();
        //2.判断结果是否为0
        //2.1不为0，没有购买资格
        if(r!=0){
            return Result.fail(r==1?"库存不足！":"不能重复下单！");
        }
        //2.2为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3优惠券id
        voucherOrder.setVoucherId(voucherId);
        //2.4用户id
        voucherOrder.setUserId(userId);
        //2.5订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.6将订单信息放入阻塞队列
        log.info("订单信息：{}", voucherOrder);
        orderTasks.add(voucherOrder);
        //3. 获取代理对象 由于Aop使用到ThreadLocal，因此选择在主线程中获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }

    /*    public Result seckillVoucher(Long voucherId) {
        //1.查询秒杀优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("没有秒杀券的信息！");
        }
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

        Long userId = UserHolder.getUser().getId();
        //5.创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
        RLock lock = redissonClient.getLock("lock:order:"+userId);
        //5.1尝试获取锁
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("不允许重复下单!");
        }
        //6.获取代理对象（事务）
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }*/


    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //1.一人一单
        //1.1 根据user_id 和 voucher_id 判断订单是否已经存在了
        int count = query().eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if(count > 0){
            log.error("用户已经抢过了！");
            return;
        }
        //2.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!success){
            log.error("库存不足！");
            return;
        }

        save(voucherOrder);
    }
}
