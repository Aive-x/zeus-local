package com.harmonycloud.zeus.controller.k8s;

import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.caas.common.model.middleware.IngressDTO;
import com.harmonycloud.zeus.service.k8s.IngressService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author dengyulong
 * @date 2021/03/23
 */
@Api(tags = {"工作台","实例列表"}, value = "中间件对外访问")
@RestController
@RequestMapping("/clusters/{clusterId}/namespaces/{namespace}/middlewares")
public class IngressController {

    @Autowired
    private IngressService ingressService;

    @ApiOperation(value = "查询中间件对外访问列表", notes = "查询中间件对外访问列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "keyword", value = "模糊搜索", paramType = "query", dataTypeClass = String.class),
    })
    @GetMapping("/ingress")
    public BaseResult<List<IngressDTO>> list(@PathVariable("clusterId") String clusterId,
                                             @PathVariable(value = "namespace") String namespace,
                                             @RequestParam(value = "keyword", required = false) String keyword) {
        return BaseResult.ok(ingressService.list(clusterId, namespace, keyword));
    }

    @ApiOperation(value = "创建中间件对外访问", notes = "创建中间件对外访问")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "ingress", value = "对外访问信息", paramType = "query", dataTypeClass = IngressDTO.class)
    })
    @PostMapping("/{middlewareName}/ingress")
    public BaseResult create(@PathVariable("clusterId") String clusterId,
                             @PathVariable("namespace") String namespace,
                             @PathVariable("middlewareName") String middlewareName,
                             @RequestBody IngressDTO ingress) {
        ingressService.create(clusterId, namespace, middlewareName, ingress);
        return BaseResult.ok();
    }

    @ApiOperation(value = "删除中间件对外访问", notes = "删除中间件对外访问")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "name", value = "对外访问名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "ingress", value = "对外访问信息", paramType = "query", dataTypeClass = IngressDTO.class)
    })
    @DeleteMapping("/{middlewareName}/ingress/{name}")
    public BaseResult delete(@PathVariable("clusterId") String clusterId,
                             @PathVariable(value = "namespace") String namespace,
                             @PathVariable("middlewareName") String middlewareName,
                             @PathVariable("name") String name,
                             @RequestBody IngressDTO ingress) {
        ingressService.delete(clusterId, namespace, middlewareName, name, ingress);
        return BaseResult.ok();
    }

    @ApiOperation(value = "获取中间件对外访问", notes = "获取中间件对外访问")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "type", value = "中间件类型", paramType = "query", dataTypeClass = String.class)
    })
    @GetMapping("/{middlewareName}/ingress")
    public BaseResult<List<IngressDTO>> get(@PathVariable("clusterId") String clusterId,
                                      @PathVariable("namespace") String namespace,
                                      @PathVariable("middlewareName") String middlewareName,
                                      @RequestParam("type") String type) {
        return BaseResult.ok(ingressService.get(clusterId, namespace, type, middlewareName));
    }

    /*@ApiOperation(value = "获取某个对外访问", notes = "获取某个对外访问")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "clusterId", value = "集群id", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "namespace", value = "命名空间", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "middlewareName", value = "中间件名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "type", value = "中间件类型", paramType = "query", dataTypeClass = String.class)
            @ApiImplicitParam(name = "name", value = "对外路由名称", paramType = "path", dataTypeClass = String.class),
            @ApiImplicitParam(name = "exposeType", value = "对外暴露方式", paramType = "query", dataTypeClass = String.class),
            @ApiImplicitParam(name = "protocol", value = "协议", paramType = "query", dataTypeClass = String.class)
    })
    @GetMapping("/{middlewareName}/ingress/{name}")
    public BaseResult<IngressDTO> get(@PathVariable("clusterId") String clusterId, @PathVariable("namespace") String namespace,
                                      @PathVariable("middlewareName") String middlewareName,
                                      @RequestParam("type") String type,
                                      @PathVariable("name") String name,
                                      @RequestParam("exposeType") String exposeType,
                                      @RequestParam("protocol") String protocol) {
        return BaseResult.ok(ingressService.get(clusterId, namespace, type, middlewareName, name, exposeType, protocol));
    }*/

}
