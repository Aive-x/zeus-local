package com.harmonycloud.zeus.controller.prometheus;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.zeus.service.prometheus.PrometheusWebhookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author xutianhong
 * @Date 2021/4/26 3:51 下午
 */
@Api(tags = "prometheusWebhook", value = "prometheus告警", description = "prometheus告警")
@RestController
@RequestMapping("/webhook")
@Slf4j
public class PrometheusWebhookController {

    @Autowired
    private PrometheusWebhookService prometheusWebhookService;

    @ApiOperation(value = "查询告警规则", notes = "查询告警规则")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "json", value = "告警内容", paramType = "query", dataTypeClass = String.class)
    })
    @PostMapping
    public BaseResult alert(@RequestBody String json) throws Exception {
        log.info(json);
        prometheusWebhookService.alert(json);
        return BaseResult.ok();
    }

}
