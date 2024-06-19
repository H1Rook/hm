package com.hmdp.service.impl;

import ch.qos.logback.classic.spi.EventArgUtil;
import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
                return Result.fail("手机号格式错误");
        }
        //3.符合，生成校验码
        String code = RandomUtil.randomNumbers(6);
        //4.保存校验码到redis //set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("Message sent, code: {}",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("invalid phone number format");
        }
        // 2.从redis校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3.不一致报错
            return Result.fail("invalid verify code");
        }
        // 4.根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
           user = creatUserByPhone(phone);
        }
        // 7.保存用户信息到redis中
        // 7.1 随机生成token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 7.2 将User对象转化为Hash存储
        Map<String, Object> userMap = new HashMap<>();
        String icon = userDTO.getIcon();
        String id = userDTO.getId().toString();
        String nickName = userDTO.getNickName();
        userMap.put("icon",icon);
        userMap.put("id",id);
        userMap.put("nickName",nickName);
        // 7.3 存储
        String tokenkey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenkey,userMap);
        // 7.4 设置token过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        // 8. 返回token
        return Result.ok(token);
    }

    private User creatUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        String s = RandomUtil.randomString(8);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+s);
        save(user);
        return user;
    }
}
