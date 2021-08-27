package com.harmonycloud.zeus.controller.middleware;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.caas.common.model.middleware.MiddlewareAlertsDTO;
import com.harmonycloud.zeus.service.middleware.MiddlewareAlertsService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

/**
 * @author xutianhong
 * @Date 2021/4/26 10:12 上午
 */
@Api(tags = {"工作台","实例列表"}, value = "中间件告警", description = "中间件告警")
@RestController
@RequestMapping("/clusters/{clusterId}/namespaces/{namespace}/middlewares/{middlewareName}/rules")
public class MiddlewareAlertsController {

    @Autowired
    private MiddlewareAlertsService middlewareAlertsService;

    @ApiOperation(value = "查询已设置告警规则", notes = "查询已设置告警规则")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "keyword", value = "关键字", paramType = "query", dataTypeClass = String.class)
    })
    @GetMapping("/used")
    public BaseResult<List<MiddlewareAlertsDTO>> listUsedRules(@PathVariable("clusterId") String clusterId,
                                                               @PathVariable("namespace") String namespace,
                                                               @PathVariable("middlewareName") String middlewareName,
                                                               @RequestParam(value = "keyword", required = false) String keyword) throws Exception {
        return BaseResult.ok(middlewareAlertsService.listUsedRules(clusterId, namespace, middlewareName, keyword));
    }

    @ApiOperation(value = "查询告警规则", notes = "查询告警规则")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "type", value = "中间件类型", paramType = "query", dataTypeClass = String.class)
    })
    @GetMapping
    public BaseResult<List<MiddlewareAlertsDTO>> listRules(@PathVariable("clusterId") String clusterId,
                                                           @PathVariable("namespace") String namespace,
                                                           @PathVariable("middlewareName") String middlewareName,
                                                           @RequestParam("type") String type) throws Exception {
        return BaseResult.ok(middlewareAlertsService.listRules(clusterId, namespace, middlewareName, type));
    }

    @ApiOperation(value = "创建告警规则", notes = "创建告警规则")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareAlertsDTO", value = "中间件告警规则", paramType = "query", dataTypeClass = MiddlewareAlertsDTO.class)
    })
    @PostMapping
    public BaseResult createRules(@PathVariable("clusterId") String clusterId,
                                  @PathVariable("namespace") String namespace,
                                  @PathVariable("middlewareName") String middlewareName,
                                  @RequestBody List<MiddlewareAlertsDTO> middlewareAlertsDTOList) throws Exception {
        middlewareAlertsService.createRules(clusterId, namespace, middlewareName, middlewareAlertsDTOList);
        return BaseResult.ok();
    }

    @ApiOperation(value = "删除告警规则", notes = "删除告警规则")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "alert", value = "告警名称", paramType = "query", dataTypeClass = String.class)
    })
    @DeleteMapping
    public BaseResult deleteRules(@PathVariable("clusterId") String clusterId,
                                  @PathVariable("namespace") String namespace,
                                  @PathVariable("middlewareName") String middlewareName,
                                  @RequestParam("alert") String alert) {
        middlewareAlertsService.deleteRules(clusterId, namespace, middlewareName, alert);
        return BaseResult.ok();
    }

}
