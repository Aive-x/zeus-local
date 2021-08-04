package com.harmonycloud.zeus.service.middleware.impl;

import java.util.List;

import com.harmonycloud.zeus.operator.api.MysqlOperator;
import com.harmonycloud.zeus.service.middleware.AbstractMiddlewareService;
import com.harmonycloud.zeus.service.middleware.MysqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.caas.common.model.middleware.Middleware;
import com.harmonycloud.caas.common.model.middleware.MysqlBackupDto;
import com.harmonycloud.caas.common.model.middleware.ScheduleBackupConfig;

/**
 * @author dengyulong
 * @date 2021/03/23
 */
@Service
public class MysqlServiceImpl extends AbstractMiddlewareService implements MysqlService {

    @Autowired
    private MysqlOperator mysqlOperator;

    @Override
    public List<MysqlBackupDto> listBackups(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName,
            MiddlewareTypeEnum.MYSQL.getType());
        return mysqlOperator.listBackups(middleware);
    }

    @Override
    public ScheduleBackupConfig getScheduleBackups(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName,
            MiddlewareTypeEnum.MYSQL.getType());
        return mysqlOperator.getScheduleBackupConfig(middleware);
    }

    @Override
    public void createScheduleBackup(String clusterId, String namespace, String middlewareName, Integer keepBackups,
        String cron) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName,
            MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.createScheduleBackup(middleware, keepBackups, cron);
    }

    @Override
    public void createBackup(String clusterId, String namespace, String middlewareName) {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName,
            MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.createBackup(middleware);
    }

    @Override
    public void deleteBackup(String clusterId, String namespace, String middlewareName, String backupFileName,
        String backupName) throws Exception {
        Middleware middleware = new Middleware(clusterId, namespace, middlewareName,
            MiddlewareTypeEnum.MYSQL.getType());
        mysqlOperator.deleteBackup(middleware, backupFileName, backupName);
    }
}
