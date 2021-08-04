package com.harmonycloud.zeus.service.system;

import org.springframework.stereotype.Service;

@Service
public interface ValidateService {

    /**
     * 判断中间件是否能够创建
     *
     * @return
     */
    boolean validateMiddleCreate(String type);

    boolean validateLicenseTime();
}
