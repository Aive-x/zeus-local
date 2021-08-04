package com.harmonycloud.zeus.config;

import com.harmonycloud.zeus.service.middleware.EsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author liyinlong
 * @description 初始化es模板
 * @date 2021/7/7 10:20 上午
 */
@Component
@Slf4j
public class InitEsTemplate implements ApplicationRunner {

    @Autowired
    private EsService esService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            log.info("应用启动完成，初始化mysql慢日志 es模板");
            esService.initEsIndexTemplate();
        } catch (Exception e) {
            e.printStackTrace();
            log.info("应用启动完成，初始化mysql慢日志 es模板失败", e);
        }
    }

}