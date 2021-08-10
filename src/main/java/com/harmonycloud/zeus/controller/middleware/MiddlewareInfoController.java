package com.harmonycloud.zeus.controller.middleware;

import com.harmonycloud.zeus.service.middleware.MiddlewareInfoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harmonycloud.caas.common.base.BaseResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

/**
 * @author dengyulong
 * @date 2021/03/23
 */
@Api(tags = "middlewareInfo", value = "中间件信息", description = "中间件信息")
@RestController
@RequestMapping("/middlewares/info")
public class MiddlewareInfoController {

    @Autowired
    private MiddlewareInfoService middlewareInfoService;

    @ApiOperation(value = "查询可用的中间件列表", notes = "查询可用的中间件列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "query", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "query", dataTypeClass = String.class)
    })
    @GetMapping
    public BaseResult list(@RequestParam(value = "clusterId") String clusterId,
                           @RequestParam(value = "namespace", required = false) String namespace) {
        if (StringUtils.isBlank(namespace)) {
            return BaseResult.ok(middlewareInfoService.list(clusterId));
        }
        return BaseResult.ok(middlewareInfoService.list(clusterId, namespace));
    }

}
