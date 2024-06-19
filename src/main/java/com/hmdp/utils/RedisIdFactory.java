package com.hmdp.utils;

import cn.hutool.Hutool;
import cn.hutool.core.util.IdUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdFactory {
    private static final long BEGIN_TIMESTAMP =  1640995200L;
    private static final int COUNT_BITS =  32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        // 1. 生成时间
        LocalDateTime now = LocalDateTime.now();
        long nowSec = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSec - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        // 2.1 获取当前日期，精确到天
        // 2.2 自增长
        String nowdate = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + nowdate);
        // 3. 拼接返回值
        return timestamp << COUNT_BITS | count ;
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
    }

}
