package com.harmonycloud.zeus.service.auth.impl;

import static com.harmonycloud.caas.filters.base.GlobalKey.SET_TOKEN;
import static com.harmonycloud.caas.filters.base.GlobalKey.USER_TOKEN;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.enums.DictEnum;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.caas.filters.token.JwtTokenComponent;
import com.harmonycloud.zeus.service.auth.AuthService;
import com.harmonycloud.tool.encrypt.PasswordUtils;

/**
 * @author dengyulong
 * @date 2021/04/02
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Value("${system.user.username:admin}")
    private String defaultUsername;
    @Value("${system.user.password:5B99164F828AED74140E5FDA077B634C}")
    private String defaultPassword;
    @Value("${system.user.expire:8}")
    private String time;

    @Override
    public JSONObject login(String username, String password, HttpServletResponse response) {
        if (!username.equals(defaultUsername)) {
            throw new BusinessException(DictEnum.USERNAME, username, ErrorMessage.NOT_EXIST);
        }
        String encryptPassword = PasswordUtils.md5(password);
        if (!encryptPassword.equals(defaultPassword)) {
            throw new BusinessException(ErrorMessage.AUTH_FAILED);
        }
        JSONObject admin = new JSONObject();
        admin.put("username", defaultUsername);
        admin.put("id", 1);
        admin.put("roleId", 1);
        long currentTime = System.currentTimeMillis();
        String token = JwtTokenComponent.generateToken("userInfo", admin,
            new Date(currentTime + Long.parseLong(time) * 3600000L), new Date(currentTime - 300000L));
        response.setHeader(SET_TOKEN, token);
        JSONObject res = new JSONObject();
        res.put("username", defaultUsername);
        res.put("token", token);
        return res;
    }

    @Override
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader(USER_TOKEN);
        if (StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("token is null");
        }
        JSONObject json = JwtTokenComponent.getClaimsFromToken("userInfo", token);
        String username = json.getString("username");
        response.setHeader(SET_TOKEN, "0");
        return username;
    }

}
