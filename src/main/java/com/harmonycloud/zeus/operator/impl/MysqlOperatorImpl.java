package com.harmonycloud.zeus.operator.impl;

import static com.harmonycloud.caas.common.constants.MinioConstant.BACKUP;
import static com.harmonycloud.caas.common.constants.MinioConstant.MINIO;
import static com.harmonycloud.caas.common.constants.NameConstant.RESOURCES;
import static com.harmonycloud.caas.common.constants.NameConstant.STORAGE;
import static com.harmonycloud.caas.common.constants.NameConstant.TYPE;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.harmonycloud.caas.common.model.middleware.*;
import com.harmonycloud.zeus.annotation.Operator;
import com.harmonycloud.zeus.integration.cluster.MysqlClusterWrapper;
import com.harmonycloud.zeus.integration.cluster.bean.BackupCRD;
import com.harmonycloud.zeus.integration.cluster.bean.BackupSpec;
import com.harmonycloud.zeus.integration.cluster.bean.BackupStorageProvider;
import com.harmonycloud.zeus.integration.cluster.bean.BackupTemplate;
import com.harmonycloud.zeus.integration.cluster.bean.Minio;
import com.harmonycloud.zeus.integration.cluster.bean.MysqlCluster;
import com.harmonycloud.zeus.integration.cluster.bean.ScheduleBackupCRD;
import com.harmonycloud.zeus.integration.cluster.bean.ScheduleBackupSpec;
import com.harmonycloud.zeus.integration.cluster.bean.Status.Condition;
import com.harmonycloud.zeus.integration.minio.MinioWrapper;
import com.harmonycloud.zeus.operator.miiddleware.AbstractMysqlOperator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.constants.NameConstant;
import com.harmonycloud.caas.common.enums.DictEnum;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.zeus.operator.api.MysqlOperator;
import com.harmonycloud.zeus.service.k8s.ClusterService;
import com.harmonycloud.zeus.service.middleware.BackupService;
import com.harmonycloud.zeus.service.middleware.ScheduleBackupService;
import com.harmonycloud.tool.date.DateUtils;
import com.harmonycloud.tool.encrypt.PasswordUtils;
import com.harmonycloud.tool.uuid.UUIDUtils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.extern.slf4j.Slf4j;

/**
 * @author dengyulong
 * @date 2021/03/23
 * 处理mysql逻辑
 */
@Slf4j
@Operator(paramTypes4One = Middleware.class)
public class MysqlOperatorImpl extends AbstractMysqlOperator implements MysqlOperator {

    @Autowired
    private MysqlClusterWrapper mysqlClusterWrapper;
    @Autowired
    private BackupService backupService;
    @Autowired
    private ScheduleBackupService scheduleBackupService;
    @Autowired
    private MinioWrapper minioWrapper;
    @Autowired
    private ClusterService clusterService;

    @Override
    public boolean support(Middleware middleware) {
        return MiddlewareTypeEnum.MYSQL == MiddlewareTypeEnum.findByType(middleware.getType());
    }

    @Override
    public void replaceValues(Middleware middleware, MiddlewareClusterDTO cluster, JSONObject values) {
        // 替换通用的值
        replaceCommonValues(middleware, cluster, values);
        MiddlewareQuota quota = middleware.getQuota().get(middleware.getType());
        replaceCommonResources(quota, values.getJSONObject(RESOURCES));
        replaceCommonStorages(quota, values);

        // mysql参数
        JSONObject mysqlArgs = values.getJSONObject("mysqlArgs");
        if (StringUtils.isBlank(middleware.getPassword())) {
            middleware.setPassword(PasswordUtils.generateCommonPassword(10));
        }
        mysqlArgs.put("root_password", middleware.getPassword());
        if (StringUtils.isNotBlank(middleware.getCharSet())) {
            mysqlArgs.put("character_set_server", middleware.getCharSet());
        }
        if (middleware.getPort() != null) {
            mysqlArgs.put("server_port", middleware.getPort());
        }

        // 备份恢复的创建
        if (StringUtils.isNotEmpty(middleware.getBackupFileName())) {
            BackupStorageProvider backupStorageProvider = backupService.getStorageProvider(middleware);
            values.put("storageProvider", JSONObject.toJSON(backupStorageProvider));
        }
    }

    @Override
    public Middleware convertByHelmChart(Middleware middleware, MiddlewareClusterDTO cluster) {
        JSONObject values = helmChartService.getInstalledValues(middleware, cluster);
        convertCommonByHelmChart(middleware, values);
        convertStoragesByHelmChart(middleware, middleware.getType(), values);

        // 处理mysql的特有参数
        if (values != null) {
            convertResourcesByHelmChart(middleware, middleware.getType(), values.getJSONObject(RESOURCES));

            JSONObject mysqlArgs = values.getJSONObject("mysqlArgs");
            middleware.setPassword(mysqlArgs.getString("root_password"));
            middleware.setCharSet(mysqlArgs.getString("character_set_server"));
            middleware.setPort(mysqlArgs.getIntValue("server_port"));
        }

        return middleware;
    }

    @Override
    public void update(Middleware middleware, MiddlewareClusterDTO cluster) {
        if (cluster == null) {
            cluster = clusterService.findById(middleware.getClusterId());
        }
        StringBuilder sb = new StringBuilder();

        // 实例扩容
        if (middleware.getQuota() != null && middleware.getQuota().get(middleware.getType()) != null) {
            MiddlewareQuota quota = middleware.getQuota().get(middleware.getType());
            // 设置limit的resources
            setLimitResources(quota);
            if (StringUtils.isNotBlank(quota.getCpu())) {
                sb.append("resources.requests.cpu=").append(quota.getCpu()).append(",resources.limits.cpu=")
                    .append(quota.getLimitCpu()).append(",");
            }
            if (StringUtils.isNotBlank(quota.getMemory())) {
                sb.append("resources.requests.memory=").append(quota.getMemory()).append(",resources.limits.memory=")
                    .append(quota.getLimitMemory()).append(",");
            }
        }

        // 修改密码
        if (StringUtils.isNotBlank(middleware.getPassword())) {
            sb.append("mysqlArgs.root_password=").append(middleware.getPassword()).append(",");
        }

        // 没有修改，直接返回
        if (sb.length() == 0) {
            return;
        }
        // 去掉末尾的逗号
        sb.deleteCharAt(sb.length() - 1);
        // 更新helm
        helmChartService.upgrade(middleware, sb.toString(), cluster);
    }

    @Override
    public void delete(Middleware middleware) {
        super.delete(middleware);

        // 删除备份
        String backupName = getBackupName(middleware);
        List<Backup> backupList = backupService.listBackup(middleware.getClusterId(), middleware.getNamespace());
        backupList.forEach(backup -> {
            if (!backup.getName().contains(backupName)) {
                return;
            }
            try {
                deleteBackup(middleware, backup.getBackupFileName(), backup.getName());
            } catch (Exception e) {
                log.error("集群：{}，命名空间：{}，mysql中间件：{}，删除mysql备份异常", middleware.getClusterId(), middleware.getNamespace(),
                    middleware.getName(), e);
            }
        });
        // 删除定时备份任务
        scheduleBackupService.delete(middleware.getClusterId(), middleware.getNamespace(), middleware.getName());

    }

    /**
     * 查询备份列表
     */
    @Override
    public List<MysqlBackupDto> listBackups(Middleware middleware) {

        // 获取Backup
        String name = getBackupName(middleware);
        List<Backup> backupList = backupService.listBackup(middleware.getClusterId(), middleware.getNamespace());
        backupList = backupList.stream().filter(backup -> backup.getName().contains(name)).collect(Collectors.toList());

        // 获取当前备份中的状态
        List<MysqlBackupDto> mysqlBackupDtoList = new ArrayList<>();

        backupList.forEach(backup -> {
            MysqlBackupDto mysqlBackupDto = new MysqlBackupDto();
            if (!"Complete".equals(backup.getPhase())) {
                mysqlBackupDto.setStatus(backup.getPhase());
                mysqlBackupDto.setBackupFileName("");
            } else {
                mysqlBackupDto.setStatus("Complete");
                mysqlBackupDto.setBackupFileName(backup.getBackupFileName());
            }
            mysqlBackupDto.setBackupName(backup.getName());
            mysqlBackupDto.setDate(DateUtils.parseUTCDate(backup.getBackupTime()));
            mysqlBackupDto.setPosition("minio(" + backup.getEndPoint() + "/" + backup.getBucketName() + ")");
            mysqlBackupDto.setType("all");
            mysqlBackupDtoList.add(mysqlBackupDto);
        });

        // 根据时间降序
        mysqlBackupDtoList.sort(
            (o1, o2) -> o1.getDate() == null ? -1 : o2.getDate() == null ? -1 : o2.getDate().compareTo(o1.getDate()));
        return mysqlBackupDtoList;
    }

    private String getBackupName(Middleware middleware) {
        return middleware.getClusterId() + "-" + middleware.getNamespace() + "-" + middleware.getName();
    }

    /**
     * 查询定时备份配置
     */
    @Override
    public ScheduleBackupConfig getScheduleBackupConfig(Middleware middleware) {
        List<ScheduleBackup> scheduleBackupList = scheduleBackupService.listScheduleBackup(middleware.getClusterId(),
            middleware.getNamespace(), middleware.getName());
        if (CollectionUtils.isEmpty(scheduleBackupList)) {
            return null;
        }
        ScheduleBackup scheduleBackup = scheduleBackupList.get(0);
        ScheduleBackupConfig scheduleBackupConfig = new ScheduleBackupConfig();
        scheduleBackupConfig.setCron(scheduleBackup.getSchedule());
        scheduleBackupConfig.setKeepBackups(scheduleBackup.getKeepBackups());
        scheduleBackupConfig.setNextBackupDate(calculateNextDate(scheduleBackup));
        return scheduleBackupConfig;
    }

    /**
     * 创建定时备份
     */
    @Override
    public void createScheduleBackup(Middleware middleware, Integer keepBackups, String cron) {
        // 校验是否运行中
        middlewareCRDService.getCRAndCheckRunning(middleware);

        Minio minio = getMinio(middleware);
        BackupTemplate backupTemplate = new BackupTemplate().setClusterName(middleware.getName())
            .setStorageProvider(new BackupStorageProvider().setMinio(minio));

        ScheduleBackupSpec spec =
            new ScheduleBackupSpec().setSchedule(cron).setBackupTemplate(backupTemplate).setKeepBackups(keepBackups);
        ObjectMeta metaData = new ObjectMeta();
        metaData.setName(getBackupName(middleware));
        Map<String, String> labels = new HashMap<>();
        labels.put("controllername", "backup-schedule-controller");
        metaData.setLabels(labels);
        metaData.setNamespace(middleware.getNamespace());
        metaData.setClusterName(middleware.getName());

        ScheduleBackupCRD scheduleBackupCRD =
            new ScheduleBackupCRD().setKind("MysqlBackupSchedule").setSpec(spec).setMetadata(metaData);
        scheduleBackupService.create(middleware.getClusterId(), scheduleBackupCRD);
    }

    /**
     * 创建备份
     */
    @Override
    public void createBackup(Middleware middleware) {
        // 校验是否运行中
        middlewareCRDService.getCRAndCheckRunning(middleware);

        String backupName = getBackupName(middleware) + "-" + UUIDUtils.get8UUID();

        BackupSpec spec = new BackupSpec().setClusterName(middleware.getName())
            .setStorageProvider(new BackupStorageProvider().setMinio(getMinio(middleware)));

        ObjectMeta metaData = new ObjectMeta();
        metaData.setName(backupName);
        Map<String, String> labels = new HashMap<>(1);
        labels.put("controllername", "backup-controller");
        metaData.setLabels(labels);
        metaData.setNamespace(middleware.getNamespace());
        metaData.setClusterName(middleware.getName());

        BackupCRD backupCRD = new BackupCRD().setKind("MysqlBackup").setSpec(spec).setMetadata(metaData);
        backupService.create(middleware.getClusterId(), backupCRD);
    }

    /**
     * 删除备份文件
     */
    @Override
    public void deleteBackup(Middleware middleware, String backupFileName, String backupName) throws Exception {
        backupService.delete(middleware.getClusterId(), middleware.getNamespace(), backupName);
        minioWrapper.removeObject(getMinio(middleware), backupFileName);
    }

    @Override
    public void switchMiddleware(Middleware middleware) {
        MysqlCluster mysqlCluster = mysqlClusterWrapper.get(middleware.getClusterId(), middleware.getNamespace(),
            middleware.getName());
        if (mysqlCluster == null) {
            throw new BusinessException(DictEnum.MYSQL_CLUSTER, middleware.getName(), ErrorMessage.NOT_EXIST);
        }
        if (!NameConstant.RUNNING.equalsIgnoreCase(mysqlCluster.getStatus().getPhase())) {
            throw new BusinessException(ErrorMessage.MIDDLEWARE_CLUSTER_IS_NOT_RUNNING);
        }
        // 手动切换
        if (handSwitch(middleware, mysqlCluster)) {
            return;
        }
        // 自动切换
        autoSwitch(middleware, mysqlCluster);

    }

    @Override
    public List<String> getConfigmapDataList(ConfigMap configMap) {
        return new ArrayList<>(Arrays.asList(configMap.getData().get("my.cnf.tmpl").split("\n")));
    }

    /**
     * 构建新configmap
     */
    @Override
    public Map<String, String> configMap2Data(ConfigMap configMap) {
        String dataString = configMap.getData().get("my.cnf.tmpl");
        Map<String, String> dataMap = new HashMap<>();
        String[] datalist = dataString.split("\n");
        for (String data : datalist) {
            if (!data.contains("=") || data.contains("#")) {
                continue;
            }
            data = data.replaceAll(" ", "");
            // 特殊处理
            if (data.contains("plugin-load")) {
                dataMap.put("plugin-load", data.replace("plugin-load=", ""));
                continue;
            }
            String[] keyValue = data.split("=");
            dataMap.put(keyValue[0].replaceAll(" ", ""), keyValue[1]);
        }
        return dataMap;
    }

    @Override
    public void editConfigMapData(CustomConfig customConfig, List<String> data) {
        for (int i = 0; i < data.size(); ++i) {
            if (data.get(i).contains(customConfig.getName())) {
                String temp = StringUtils.substring(data.get(i), data.get(i).indexOf("=") + 1, data.get(i).length());
                if (data.get(i).replace(" ", "").replace(temp, "").replace("=", "").equals(customConfig.getName())) {
                    data.set(i, data.get(i).replace(temp, customConfig.getValue()));
                }
            }
        }
    }

    /**
     * 转换data为map形式
     */
    @Override
    public void updateConfigData(ConfigMap configMap, List<String> data) {
        // 构造新configmap
        StringBuilder temp = new StringBuilder();
        for (String str : data) {
            {
                temp.append(str).append("\n");
            }
        }
        configMap.getData().put("my.cnf.tmpl", temp.toString());
    }

    /**
     * 手动切换
     */
    private boolean handSwitch(Middleware middleware, MysqlCluster mysqlCluster) {
        // 不等于null，自动切换，无需处理
        if (middleware.getAutoSwitch() != null) {
            // false为无需切换，true为已切换
            return false;
        }
        String masterName = null;
        String slaveName = null;
        for (Condition cond : mysqlCluster.getStatus().getConditions()) {
            if ("master".equalsIgnoreCase(cond.getType())) {
                masterName = cond.getName();
            } else if ("slave".equalsIgnoreCase(cond.getType())) {
                slaveName = cond.getName();
            }
        }
        if (masterName == null || slaveName == null) {
            throw new BusinessException(ErrorMessage.MIDDLEWARE_CLUSTER_POD_ERROR);
        }
        mysqlCluster.getSpec().getClusterSwitch().setFinished(false).setSwitched(false).setMaster(slaveName);
        try {
            mysqlClusterWrapper.update(middleware.getClusterId(), middleware.getNamespace(), mysqlCluster);
        } catch (IOException e) {
            log.error("集群id:{}，命名空间:{}，mysql集群:{}，手动切换异常", middleware.getClusterId(), middleware.getNamespace(),
                middleware.getName(), e);
            throw new BusinessException(DictEnum.MYSQL_CLUSTER, middleware.getName(), ErrorMessage.SWITCH_FAILED);
        }
        return true;
    }

    /**
     * 自动切换
     */
    private void autoSwitch(Middleware middleware, MysqlCluster mysqlCluster) {
        boolean changeStatus = false;
        if (mysqlCluster.getSpec().getPassiveSwitched() == null) {
            if (!middleware.getAutoSwitch()) {
                changeStatus = true;
                mysqlCluster.getSpec().setPassiveSwitched(true);
            }
        } else if (mysqlCluster.getSpec().getPassiveSwitched().equals(middleware.getAutoSwitch())) {
            changeStatus = true;
            mysqlCluster.getSpec().setPassiveSwitched(!middleware.getAutoSwitch());
        }
        if (changeStatus) {
            try {
                mysqlClusterWrapper.update(middleware.getClusterId(), middleware.getNamespace(), mysqlCluster);
            } catch (IOException e) {
                log.error("集群id:{}，命名空间:{}，mysql集群:{}，开启/关闭自动切换异常", middleware.getClusterId(),
                    middleware.getNamespace(), middleware.getName(), e);
                throw new BusinessException(DictEnum.MYSQL_CLUSTER, middleware.getName(), ErrorMessage.SWITCH_FAILED);
            }
        }
    }

    /**
     * 获取minio
     */
    public Minio getMinio(Middleware middleware) {
        MiddlewareClusterDTO cluster = clusterService.findById(middleware.getClusterId());
        // 获取minio的数据
        Object backupObj = cluster.getStorage().get(BACKUP);
        if (backupObj == null) {
            throw new BusinessException(ErrorMessage.MIDDLEWARE_BACKUP_STORAGE_NOT_EXIST);
        }
        JSONObject backup = JSONObject.parseObject(JSONObject.toJSONString(backupObj));
        if (backup == null || !MINIO.equals(backup.getString(TYPE))) {
            throw new BusinessException(ErrorMessage.MIDDLEWARE_BACKUP_STORAGE_NOT_EXIST);
        }

        return JSONObject.toJavaObject(backup.getJSONObject(STORAGE), Minio.class);
    }

    /**
     * 计算下次备份时间
     */
    public Date calculateNextDate(ScheduleBackup scheduleBackup) {
        try {
            String[] cron = scheduleBackup.getSchedule().split(" ");
            String[] cronWeek = cron[4].split(",");
            List<Date> dateList = new ArrayList<>();
            for (String dayOfWeek : cronWeek) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MINUTE, Integer.parseInt(cron[0]));
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(cron[1]));
                cal.set(Calendar.DAY_OF_WEEK, Integer.parseInt(dayOfWeek) + 1);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                Date date = cal.getTime();
                dateList.add(date);
            }
            dateList.sort((d1, d2) -> {
                if (d1.equals(d2)) {
                    return 0;
                }
                return d1.before(d2) ? -1 : 1;
            });
            Date now = new Date();
            for (Date date : dateList) {
                if (now.before(date)) {
                    return date;
                }
            }
            return DateUtils.addInteger(dateList.get(0), Calendar.DATE, 7);
        } catch (Exception e) {
            log.error("定时备份{} ,计算下次备份时间失败", scheduleBackup.getName());
            return null;
        }
    }

}
