package com.harmonycloud.zeus.service.auth;

import com.alibaba.fastjson.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author dengyulong
 * @date 2021/04/02
 */
public interface AuthService {

    /**
     * 登录
     *
     * @param username 用户名
     * @param password 密码
     * @param response 响应
     * @return
     */
    JSONObject login(String username, String password, HttpServletResponse response);

    /**
     * 登出
     *
     * @param request  请求
     * @param response 响应
     * @return
     */
    String logout(HttpServletRequest request, HttpServletResponse response);

}
