package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * get phone verify code
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {

        return userService.sendCode(phone,session);
    }


    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // login
        return userService.login(loginForm,session);
    }


    @PostMapping("/logout")
    public Result logout(){
        // log out
        return Result.fail("unfinished ");
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();

        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // check detail
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // first time
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // return
        return Result.ok(info);
    }
}
