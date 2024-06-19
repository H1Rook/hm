package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.intern.InternUtil;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryAllTypeList() {
        // 1.查询redis中是否有shoptypeJson
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST);
        // 2.判断json是否为空
        if (StrUtil.isNotBlank(json)){
            List<ShopType> list = JSONUtil.toList(json, ShopType.class);
        // 3.不为空返回list集合
            return Result.ok(list);
        }
        // 4.若为空查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        // 5.判断查询数据是否为空
        if (list == null){
        // 6.list查询结果为空，则返回异常
            return Result.fail("System error");
        }
        // 7.将数据存入redis中
        String jsonStr = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST,jsonStr);
        // 8.返回数据list到前端
        return Result.ok(list);
    }
}
