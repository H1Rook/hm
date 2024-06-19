package com.hmdp.service.impl;

import ch.qos.logback.classic.spi.EventArgUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdFactory;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

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
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdFactory redisIdFactory;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
   /* @Override
    public Result seckillVoucher(Long voucherID){
        Long userID = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherID.toString(), userID.toString()
        );
        // 2.判断结果是为0
        int r = result.intValue();
        if (r != 0) {
            return Result.fail( r == 1? "库存不足" : "不可重复下单");
        }
        // 2.1 不为0，代表没有
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdFactory.nextId("order");
        voucherOrder.setVoucherId(voucherID);
        voucherOrder.setUserId(userID);
        voucherOrder.setId(orderId)


        return Result.ok(orderId);
    }*/
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("Event has not started yet");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("Event had ended");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("Sold out!!!");
        }
        return createVoucherOrder(voucherId);
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        // 5.1 查询用户id
        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean islocked = lock.tryLock();
        if (!islocked) {
            return Result.fail("Voucher is already exist");
        }
        try {
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0 ){
                // 判断是否有单
                return Result.fail("Voucher is already exist");
            }
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1 ") // set stock = stock -1
                    .eq("voucher_id", voucherId) // where id = ? and stock = ？
                    .gt("stock",0)
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("Sold out");
            }
                // 7. 生成新的订单
                VoucherOrder voucherOrder = new VoucherOrder();
                // 7.1 订单id
                long orderId = redisIdFactory.nextId("order");
                voucherOrder.setId(orderId);
                voucherOrder.setUserId(userId);
                // 7.2 代金卷id
                voucherOrder.setVoucherId(voucherId);
                save(voucherOrder);
                // 7.3 返回订单id
                return Result.ok(orderId);
        }finally {
            lock.unlock();
        }
    }
}
