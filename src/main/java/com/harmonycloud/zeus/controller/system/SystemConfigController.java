package com.harmonycloud.zeus.controller.system;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.zeus.service.system.SystemConfigService;
import com.harmonycloud.tool.encrypt.Base64Utils;
import com.harmonycloud.tool.encrypt.RSAUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Api(tags = "systemConfigs", value = "系统配置", description = "系统配置")
@RestController
@RequestMapping("/system/configs")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @ApiOperation(value = "查看license", notes = "查看license")
    @ResponseBody
    @RequestMapping(value = "/license", method = RequestMethod.GET)
    public BaseResult getSystemConfigLicense() throws Exception {
        return systemConfigService.getSystemConfigLicense();
    }

    @ApiOperation(value = "更新license", notes = "更新license")
    @ResponseBody
    @RequestMapping(value = "/license", method = RequestMethod.PUT)
    public BaseResult updateSystemConfigLicense(@RequestParam("license") String license) {
        systemConfigService.saveSystemConfigLicense(license);
        return BaseResult.ok();
    }

    @ApiOperation(value = "生成license", notes = "生成license")
    @ResponseBody
    @RequestMapping(value = "/encrypt", method = RequestMethod.POST)
    public BaseResult generateLicense(@RequestParam(value = "content") String content,
        @RequestParam(value = "key") String key) throws Exception {
        String encrypt = Base64Utils.encode(RSAUtils.encryptByPublicKey(content.getBytes(), key));
        return BaseResult.ok(encrypt);
    }

}
