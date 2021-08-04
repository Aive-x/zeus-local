package com.harmonycloud.zeus.controller.auth;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.zeus.service.auth.AuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author dengyulong
 * @date 2021/04/02
 */
@Api(tags = "auth", value = "授权认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @ApiOperation(value = "登录", notes = "登录")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "username", value = "用户名", paramType = "query", dataTypeClass = String.class),
        @ApiImplicitParam(name = "password", value = "密码", paramType = "query", dataTypeClass = String.class)
    })
    @PostMapping("/login")
    public BaseResult<JSONObject> login(@RequestParam("username") String username,
        @RequestParam("password") String password,
        HttpServletResponse response) {
        return BaseResult.ok(authService.login(username, password, response));
    }

    @ApiOperation(value = "登出", notes = "登出")
    @PostMapping("/logout")
    public BaseResult<String> logout(HttpServletRequest request,
        HttpServletResponse response) {
        return BaseResult.ok(authService.logout(request, response));
    }

}
