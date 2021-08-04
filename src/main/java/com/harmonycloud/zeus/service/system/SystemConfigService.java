package com.harmonycloud.zeus.service.system;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.zeus.bean.BeanSystemConfig;
import org.springframework.stereotype.Service;

@Service
public interface SystemConfigService {

    BeanSystemConfig findByConfigName(String configName);

    BaseResult getSystemConfigLicense() throws Exception;

    void saveSystemConfigLicense(String license);

    /**
     * 判断license是否可用
     *
     * @return
     */
    Boolean getLicenseStatus();

}
