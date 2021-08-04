package com.harmonycloud.zeus.service.system.impl;

import com.alibaba.fastjson.JSONObject;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.ImmutableMap;
import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.caas.common.constants.CommonConstant;
import com.harmonycloud.caas.common.constants.DateStyle;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.caas.common.exception.CaasRuntimeException;
import com.harmonycloud.zeus.bean.BeanMiddlewareInfo;
import com.harmonycloud.zeus.bean.BeanSystemConfig;
import com.harmonycloud.zeus.dao.BeanSystemConfigMapper;
import com.harmonycloud.zeus.service.middleware.MiddlewareInfoService;
import com.harmonycloud.zeus.service.middleware.MiddlewareService;
import com.harmonycloud.zeus.service.system.SystemConfigService;
import com.harmonycloud.tool.date.DateUtils;
import com.harmonycloud.tool.encrypt.RSAUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${license.public.key}")
    private String PUBLIC_KEY;

    @Autowired
    private BeanSystemConfigMapper systemConfigMapper;
    @Autowired
    private MiddlewareService middlewareService;
    @Autowired
    private MiddlewareInfoService middlewareInfoService;

    @Override
    public BeanSystemConfig findByConfigName(String configName) {
        QueryWrapper<BeanSystemConfig> wrapper = new QueryWrapper<BeanSystemConfig>().eq("config_name", configName);
        return this.systemConfigMapper.selectOne(wrapper);
    }

    /**
     * save config (create/update)
     *
     * @param configName
     * @param configValue
     * @param configType
     * @param username
     */
    private void saveConfig(String configName, String configValue, String configType, String username) {
        // if blank then do nothing
        if (StringUtils.isBlank(configValue)) {
            return;
        }
        // query config by name
        QueryWrapper<BeanSystemConfig> wrapper = new QueryWrapper<BeanSystemConfig>().eq("config_name", "license");
        BeanSystemConfig config = this.systemConfigMapper.selectOne(wrapper);

        // create config
        if (config == null) {
            config = new BeanSystemConfig();
            config.setConfigName(configName);
            config.setConfigValue(configValue);
            config.setConfigType(configType);
            config.setCreateTime(LocalDateTime.now());
            if (username != null) {
                config.setCreateUser(username);
            }
            this.systemConfigMapper.insert(config);
            return;
        }

        // update config
        config.setConfigValue(configValue);
        config.setUpdateTime(LocalDateTime.now());
        if (username != null) {
            config.setUpdateUser(username);
        }
        this.systemConfigMapper.updateById(config);
    }

    @Override
    public BaseResult getSystemConfigLicense() {
        JSONObject licenseInfo;
        BeanSystemConfig systemConfig = this.findByConfigName(CommonConstant.LICENSE);
        if (Objects.isNull(systemConfig) || StringUtils.isEmpty(systemConfig.getConfigValue())) {
            licenseInfo = new JSONObject();
            licenseInfo.put(CommonConstant.LICENSE_STATUS, CommonConstant.LICENSE_STATUS_UNAUTHORIZED);
            return BaseResult.ok(licenseInfo);
        }
        try {
            String plaintext = new String(RSAUtils.decryptByPrivateKey(systemConfig.getConfigValue(), PUBLIC_KEY));
            licenseInfo = JSONObject.parseObject(plaintext);
        } catch (Exception e) {
            logger.error("license解密或类型转换失败", e);
            throw new CaasRuntimeException(ErrorMessage.LICENSE_DECODE_ERROR);
        }

        String invalidTimeObj = licenseInfo.getString(CommonConstant.LICENSE_INVALID_TIME);
        if (StringUtils.isNotBlank(invalidTimeObj) && Long.parseLong(invalidTimeObj) < System.currentTimeMillis()) {
            licenseInfo = new JSONObject();
            licenseInfo.put(CommonConstant.LICENSE_STATUS, CommonConstant.LICENSE_STATUS_INVALID);
            return BaseResult.ok(licenseInfo);
        }
        List<BeanMiddlewareInfo> mwInfoList = middlewareInfoService.list(null);
        Map<String, List<BeanMiddlewareInfo>> mwInfoMap =
            mwInfoList.stream().collect(Collectors.groupingBy(BeanMiddlewareInfo::getChartName));

        licenseInfo.put(CommonConstant.LICENSE, systemConfig.getConfigValue());
        licenseInfo.put(CommonConstant.LICENSE_EFFECT_TIME, systemConfig.getUpdateTime());
        licenseInfo.put(CommonConstant.LICENSE_STATUS, CommonConstant.LICENSE_STATUS_AUTHORIZED);
        List middleList = new ArrayList<>();
        if (!ObjectUtils.isEmpty(licenseInfo.get((MiddlewareTypeEnum.MYSQL.getType())))) {
            middleList.add(ImmutableMap.of("total", licenseInfo.get(MiddlewareTypeEnum.MYSQL.getType()), "used",
                middlewareService.simpleListAll(MiddlewareTypeEnum.MYSQL.getType()).size(), "middlewareInfo",
                mwInfoMap.get(MiddlewareTypeEnum.MYSQL.getType()).get(0)));
        }
        if (!ObjectUtils.isEmpty(licenseInfo.get((MiddlewareTypeEnum.REDIS.getType())))) {
            middleList.add(ImmutableMap.of("total", licenseInfo.get(MiddlewareTypeEnum.REDIS.getType()), "used",
                middlewareService.simpleListAll(MiddlewareTypeEnum.REDIS.getType()).size(), "middlewareInfo",
                mwInfoMap.get(MiddlewareTypeEnum.REDIS.getType()).get(0)));
        }
        if (!ObjectUtils.isEmpty(licenseInfo.get((MiddlewareTypeEnum.ELASTIC_SEARCH.getType())))) {
            middleList.add(ImmutableMap.of("total", licenseInfo.get(MiddlewareTypeEnum.ELASTIC_SEARCH.getType()),
                "used", middlewareService.simpleListAll(MiddlewareTypeEnum.ELASTIC_SEARCH.getType()).size(),
                "middlewareInfo", mwInfoMap.get(MiddlewareTypeEnum.ELASTIC_SEARCH.getType()).get(0)));
        }
        if (!ObjectUtils.isEmpty(licenseInfo.get((MiddlewareTypeEnum.ROCKET_MQ.getType())))) {
            middleList.add(ImmutableMap.of("total", licenseInfo.get(MiddlewareTypeEnum.ROCKET_MQ.getType()), "used",
                middlewareService.simpleListAll(MiddlewareTypeEnum.ROCKET_MQ.getType()).size(), "middlewareInfo",
                mwInfoMap.get(MiddlewareTypeEnum.ROCKET_MQ.getType()).get(0)));
        }
        licenseInfo.put("middleList", middleList);
        return BaseResult.ok(licenseInfo);
    }

    @Override
    public void saveSystemConfigLicense(String license) {
        if (StringUtils.isEmpty(license)) {
            throw new CaasRuntimeException(ErrorMessage.PARAMETER_VALUE_NOT_PROVIDE);
        }
        // 校验license
        JSONObject licenseInfo = checkLicense(license);
        // 保存license
        saveConfig(CommonConstant.LICENSE, license, CommonConstant.LICENSE, null);
    }

    private JSONObject checkLicense(String license) {
        JSONObject licenseInfo;
        // 验证格式
        try {
            String plaintext = new String(RSAUtils.decryptByPrivateKey(license, PUBLIC_KEY));
            licenseInfo = JSONObject.parseObject(plaintext);
            // 验证失效时间
            String invalidTimeStr = licenseInfo.getString(CommonConstant.LICENSE_INVALID_TIME);
            if (StringUtils.isNotBlank(invalidTimeStr)) {
                long invalidTime = Long.parseLong(invalidTimeStr);
                String date = DateUtils.DateToString(new Date(invalidTime), DateStyle.YYYY_MM_DD_HH_MM_SS);
                licenseInfo.put(CommonConstant.LICENSE_INVALID_TIME, date);

                if (invalidTime < System.currentTimeMillis()) {
                    logger.error("失效时间不合法!失效时间：{}", date);
                    throw new CaasRuntimeException(ErrorMessage.LICENSE_INVALID_TIME_ERROR);
                }
            }
        } catch (Exception e) {
            logger.error("license解密或类型转换失败!license：{}", license);
            e.printStackTrace();
            throw new CaasRuntimeException(ErrorMessage.LICENSE_DECODE_ERROR);
        }

        return licenseInfo;
    }

    @Override
    public Boolean getLicenseStatus() {
        JSONObject licenseInfo;
        BeanSystemConfig systemConfig = this.findByConfigName(CommonConstant.LICENSE);
        if (Objects.isNull(systemConfig) || StringUtils.isEmpty(systemConfig.getConfigValue())) {
            return false;
        }
        try {
            String plaintext = new String(RSAUtils.decryptByPrivateKey(systemConfig.getConfigValue(), PUBLIC_KEY));
            licenseInfo = JSONObject.parseObject(plaintext);
        } catch (Exception e) {
            logger.error("license解密或类型转换失败：license：{}，private_key：{}", systemConfig.getConfigValue(), PUBLIC_KEY, e);
            throw new CaasRuntimeException(ErrorMessage.LICENSE_DECODE_ERROR);
        }

        String invalidTimeStr = licenseInfo.getString(CommonConstant.LICENSE_INVALID_TIME);
        if (StringUtils.isNotBlank(invalidTimeStr) && Long.parseLong(invalidTimeStr) < System.currentTimeMillis()) {
            return false;
        }
        return true;
    }

}
