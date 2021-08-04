package com.harmonycloud.zeus.service.system.impl;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.constants.CommonConstant;
import com.harmonycloud.caas.common.constants.DateStyle;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.caas.common.model.middleware.Middleware;
import com.harmonycloud.zeus.bean.BeanSystemConfig;
import com.harmonycloud.zeus.service.middleware.MiddlewareService;
import com.harmonycloud.zeus.service.system.SystemConfigService;
import com.harmonycloud.zeus.service.system.ValidateService;
import com.harmonycloud.tool.date.DateUtils;
import com.harmonycloud.tool.encrypt.RSAUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ValidateServiceImpl implements ValidateService {

    @Value("${license.public.key}")
    private String PUBLIC_KEY;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private MiddlewareService middlewareService;

    @Override
    public boolean validateMiddleCreate(String type) {
        JSONObject licenseInfo = this.getLicenseInfo();
        Integer middleLimit = (Integer)licenseInfo.get(type);
        if (Objects.isNull(middleLimit)) {
            return true;
        }
        if (middleLimit == CommonConstant.LICENSE_UNLIMIT) {
            return true;
        }
        List<Middleware> midiDeviceList = middlewareService.simpleListAll(type);
        if (midiDeviceList.size() >= middleLimit) {
            return false;
        }
        return true;
    }

    @Override
    public boolean validateLicenseTime() {
        JSONObject licenseInfo = this.getLicenseInfo();
        // 验证失效时间
        String invalidTimeStr = licenseInfo.getString(CommonConstant.LICENSE_INVALID_TIME);
        if (StringUtils.isNotBlank(invalidTimeStr)) {
            long invalidTime = Long.parseLong(invalidTimeStr);
            String date = DateUtils.DateToString(new Date(invalidTime), DateStyle.YYYY_MM_DD_HH_MM_SS);
            licenseInfo.put(CommonConstant.LICENSE_INVALID_TIME, date);
            if (invalidTime < System.currentTimeMillis()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取并解密license信息
     *
     * @return
     */
    private JSONObject getLicenseInfo() {
        JSONObject licenseInfo;
        BeanSystemConfig systemConfig = systemConfigService.findByConfigName(CommonConstant.LICENSE);
        if (Objects.isNull(systemConfig) || StringUtils.isEmpty(systemConfig.getConfigValue())) {
            log.error("license为空");
            throw new BusinessException(ErrorMessage.LICENSE_NULL_ERROR);
        }
        try {
            String plaintext = new String(RSAUtils.decryptByPrivateKey(systemConfig.getConfigValue(), PUBLIC_KEY));
            licenseInfo = JSONObject.parseObject(plaintext);
        } catch (Exception e) {
            log.error("license解密或类型转换失败", e);
            throw new BusinessException(ErrorMessage.LICENSE_DECODE_ERROR);
        }
        return licenseInfo;
    }

}
