package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryByid(Long id) {
      // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(
        CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 用互斥锁解决缓存击穿
       /* Shop shop = queryWithMutex(id);
         // 逻辑过期缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient
                .queryWithLogicalExpire(
                        CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);*/
        if (shop == null) {
            return Result.fail("shop doesn't not exist");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

/*    public Shop queryWithLogicalExpire(Long id){
        // 1.从redis查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
        // 3.不存在直接返回
            return null;
        }
        // 4.命中判断过期时间，先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);

        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
        // 5.1 未过期，直接返回店铺信息
        return shop;
        }
        // 6. 已过期，缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLocked = tryLock(lockKey);
        // 6.2 判断是否获取互斥锁成功
        if (isLocked) {
        // 6.3 成功，开启独立线程实现缓存重建
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                // 重建缓存
                this.saveShop2Redis(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                // 释放锁
                unlock(lockKey);
            }
        });
        }
        // 6.4 返回商铺信息
        return shop;
    }*/
    public Shop queryWithMutex(Long id){
        // 1.从redis查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        // 4.实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            boolean islock = tryLock(lockKey);
        // 4.2 判断是否获取成功
            if (!islock) {
        // 4.3 失败，则休眠并重试
                Thread.sleep(50);
               return queryWithMutex(id);
            }
        // 4.4 成功，根据id查询数据库
            shop = getById(id);
            Thread.sleep(200);
        // 5.不存在，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
        // 6.存在写入redis
            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
        // 7. 释放互斥锁
            unlock(lockKey);
        }
        // 8. 返回
        return shop;
    }

   /* public Shop queryWithPassThrough(Long id){
        // 1.从redis查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        // 4.不存在，根据id查询数据库
        Shop shop = getById(id);
        // 5.不存在，返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在写入redis
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return shop;
    }*/

    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long seconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(seconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("invalid shop entity");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());

        return Result.ok();
    }
}
