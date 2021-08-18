package com.harmonycloud.zeus.service.middleware.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import com.harmonycloud.caas.common.base.BaseResult;
import com.harmonycloud.caas.common.model.middleware.*;
import com.harmonycloud.zeus.service.k8s.IngressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.zeus.operator.api.MysqlOperator;
import com.harmonycloud.zeus.service.middleware.AbstractMiddlewareService;
import com.harmonycloud.zeus.service.middleware.MysqlService;
import org.springframework.util.CollectionUtils;

import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.MIDDLEWARE_EXPOSE_NODEPORT;

/**
 * @author dengyulong
 * @date 2021/03/23
 */
@Slf4j
@Service
public class MysqlServiceImpl extends AbstractMiddlewareService implements MysqlService {

    @Autowired
    private MysqlOperator mysqlOperator;
    @Autowired
    private IngressService ingressService;
    @Autowired
    private MiddlewareServiceImpl middlewareService;

    @Override
    public List<MysqlBackupDto> listBackups(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        return mysqlOperator.listBackups(middleware);
    }

    @Override
    public ScheduleBackupConfig getScheduleBackups(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        return mysqlOperator.getScheduleBackupConfig(middleware);
    }

    @Override
    public void createScheduleBackup(String clusterId, String namespace, String middlewareName,Integer keepBackups, String cron) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.createScheduleBackup(middleware, keepBackups, cron);
    }

    @Override
    public void createBackup(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.createBackup(middleware);
    }

    @Override
    public void deleteBackup(String clusterId, String namespace, String middlewareName, String backupFileName, String backupName) throws Exception{
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.deleteBackup(middleware, backupFileName, backupName);
    }

    @Override
    public BaseResult switchDisasterRecovery(String clusterId, String namespace, String middlewareName) {
        try {
            mysqlOperator.switchDisasterRecovery(clusterId, namespace, middlewareName);
            return BaseResult.ok();
        } catch (Exception e) {
            log.error("灾备切换失败", e);
            return BaseResult.error();
        }
    }

    @Override
    public BaseResult queryAccessInfo(String clusterId, String namespace, String middlewareName) {
        // 获取对外访问信息
        Middleware middleware = middlewareService.detail(clusterId, namespace, middlewareName, MiddlewareTypeEnum.MYSQL.getType());
        JSONObject res = new JSONObject();
        JSONObject source = queryAllAccessInfo(clusterId, namespace, middlewareName);
        res.put("source", source);
        MysqlDTO mysqlDTO = middleware.getMysqlDTO();
        if (mysqlDTO != null) {
            Boolean isSource = mysqlDTO.getIsSource();
            if (isSource != null) {
                JSONObject disasterRecovery = queryAllAccessInfo(mysqlDTO.getRelationClusterId(), mysqlDTO.getRelationNamespace(), mysqlDTO.getRelationName());
                res.put("disasterRecovery", disasterRecovery);
            }
        }
        return BaseResult.ok(res);
    }

    public JSONObject queryAllAccessInfo(String clusterId, String namespace, String middlewareName) {
        List<IngressDTO> ingressDTOS = ingressService.get(clusterId, namespace, MiddlewareTypeEnum.MYSQL.name(), middlewareName);
        List<IngressDTO> serviceDTOList = ingressDTOS.stream().filter(ingressDTO -> (
                ingressDTO.getName().contains("headless") && ingressDTO.getExposeType().equals(MIDDLEWARE_EXPOSE_NODEPORT))
        ).collect(Collectors.toList());

        JSONObject mysqlInfo = new JSONObject();
        if (!CollectionUtils.isEmpty(serviceDTOList)) {
            IngressDTO ingressDTO = serviceDTOList.get(0);
            String exposeIP = ingressDTO.getExposeIP();
            List<ServiceDTO> serviceList = ingressDTO.getServiceList();
            if (!CollectionUtils.isEmpty(serviceList)) {
                ServiceDTO serviceDTO = serviceList.get(0);
                String exposePort = serviceDTO.getExposePort();
                mysqlInfo.put("address", exposeIP + ":" + exposePort);
            }
            mysqlInfo.put("username", "root");
        }
        return mysqlInfo;
    }
}
