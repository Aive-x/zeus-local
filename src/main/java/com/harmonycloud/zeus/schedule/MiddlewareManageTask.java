package com.harmonycloud.zeus.schedule;

import com.harmonycloud.zeus.service.middleware.impl.MiddlewareServiceImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.harmonycloud.caas.common.model.middleware.Middleware;
import com.harmonycloud.caas.common.model.middleware.MiddlewareClusterDTO;
import com.harmonycloud.zeus.operator.BaseOperator;

import lombok.extern.slf4j.Slf4j;

/**
 * @author dengyulong
 * @date 2021/04/14
 * 中间件管理的异步任务
 */
@Slf4j
@Component
public class MiddlewareManageTask {

    /**
     * 异步创建中间件
     *
     * @param middleware 中间件信息
     * @param cluster    集群
     * @param operator   operator实现
     */
    @Async("singleThreadExecutor")
    public void asyncCreate(Middleware middleware, MiddlewareClusterDTO cluster, BaseOperator operator) {
        operator.create(middleware, cluster);
    }

    /**
     * 异步修改中间件
     */
    @Async("taskExecutor")
    public void asyncUpdate(Middleware middleware, MiddlewareClusterDTO cluster, BaseOperator operator) {
        operator.update(middleware, cluster);
    }

    /**
     * 异步删除中间件
     */
    @Async("taskExecutor")
    public void asyncDelete(Middleware middleware, BaseOperator operator) {
        operator.delete(middleware);
    }

    /**
     * 异步主从切换
     */
    @Async("taskExecutor")
    public void asyncSwitch(Middleware middleware, BaseOperator operator) {
        operator.switchMiddleware(middleware);
    }

    /**
     * 异步创建NodePort对外服务
     * @param middleware
     * @param middlewareService
     */
    @Async("singleThreadExecutor")
    public void asyncCreateNodePortService(Middleware middleware, MiddlewareServiceImpl middlewareService){
        middlewareService.createOpenService(middleware);
    }

    /**
     * 异步创建mysql源实例和灾备实例关联关系
     * @param middleware
     * @param middlewareService
     */
    @Async("singleThreadExecutor")
    public void asyncCreateMysqlReplicate(Middleware middleware,Middleware disasterRecovery, MiddlewareServiceImpl middlewareService){
        middlewareService.createMysqlReplicate(middleware, disasterRecovery);
    }
}
