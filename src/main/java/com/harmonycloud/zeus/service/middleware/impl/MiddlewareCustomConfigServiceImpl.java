package com.harmonycloud.zeus.service.middleware.impl;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.harmonycloud.zeus.bean.BeanCustomConfig;
import com.harmonycloud.zeus.bean.BeanCustomConfigHistory;
import com.harmonycloud.zeus.dao.BeanCustomConfigHistoryMapper;
import com.harmonycloud.zeus.dao.BeanCustomConfigMapper;
import com.harmonycloud.zeus.service.AbstractBaseService;
import com.harmonycloud.zeus.service.k8s.ClusterService;
import com.harmonycloud.zeus.service.k8s.ConfigMapService;
import com.harmonycloud.zeus.service.k8s.PodService;
import com.harmonycloud.zeus.service.middleware.MiddlewareCustomConfigService;
import com.harmonycloud.zeus.service.registry.HelmChartService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.exception.CaasRuntimeException;
import com.harmonycloud.caas.common.model.middleware.*;
import com.harmonycloud.caas.common.model.registry.HelmChartFile;
import com.harmonycloud.caas.common.util.ThreadPoolExecutorFactory;
import com.harmonycloud.tool.date.DateUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * @author xutianhong
 * @Date 2021/4/23 4:32 下午
 */
@Service
@Slf4j
public class MiddlewareCustomConfigServiceImpl extends AbstractBaseService implements MiddlewareCustomConfigService {

    @Autowired
    private BeanCustomConfigHistoryMapper beanCustomConfigHistoryMapper;
    @Autowired
    private PodService podService;
    @Autowired
    protected ConfigMapService configMapService;
    @Autowired
    private BeanCustomConfigMapper beanCustomConfigMapper;
    @Autowired
    private HelmChartService helmChartService;
    @Autowired
    private ClusterService clusterService;

    @Override
    public List<CustomConfig> listCustomConfig(String clusterId, String namespace, String middlewareName, String type)
        throws Exception {

        Middleware middleware = new Middleware(clusterId, namespace, middlewareName, type);
        MiddlewareClusterDTO cluster = clusterService.findById(clusterId);
        //获取values
        JSONObject values = helmChartService.getInstalledValues(middlewareName, namespace, cluster);
        //获取configs
        Map<String, String> data = getConfigFromValues(middleware, values);
        //取出chartVersion
        middleware.setChartVersion(values.getString("chart-version"));
        // 获取数据库数据
        QueryWrapper<BeanCustomConfig> wrapper = new QueryWrapper<BeanCustomConfig>()
            .eq("chart_name", middleware.getType()).eq("chart_version", middleware.getChartVersion());
        List<BeanCustomConfig> beanCustomConfigList = beanCustomConfigMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(beanCustomConfigList)) {
            HelmChartFile helmChart =
                helmChartService.getHelmChartFromRegistry(clusterId, namespace, middlewareName, type);
            beanCustomConfigList.addAll(updateConfig2MySQL(helmChart, false));
        }
        // 封装customConfigList
        List<CustomConfig> customConfigList = new ArrayList<>();
        beanCustomConfigList.forEach(beanCustomConfig -> {
            CustomConfig customConfig = new CustomConfig();
            BeanUtils.copyProperties(beanCustomConfig, customConfig);
            customConfig.setValue(data.getOrDefault(customConfig.getName(), ""));
            customConfig.setParamType(customConfig.getRanges().contains("|") ? "select" : "input");
            if ("sql_mode".equals(beanCustomConfig.getName())){
                customConfig.setParamType("multiSelect");
            }
            customConfigList.add(customConfig);
        });
        return customConfigList;
    }

    @Override
    public void updateCustomConfig(MiddlewareCustomConfig config) {
        MiddlewareClusterDTO cluster = clusterService.findById(config.getClusterId());
        Middleware middleware =
            new Middleware(config.getClusterId(), config.getNamespace(), config.getName(), config.getType());
        //获取values
        JSONObject values = helmChartService.getInstalledValues(config.getName(), config.getNamespace(), cluster);
        //获取configs
        Map<String, String> data = getConfigFromValues(middleware, values);
        if (CollectionUtils.isEmpty(data)) {
            // 从parameter.yaml文件创建一份
            QueryWrapper<BeanCustomConfig> wrapper = new QueryWrapper<BeanCustomConfig>()
                    .eq("chart_name", middleware.getType()).eq("chart_version", values.getString("chart-version"));
            List<BeanCustomConfig> beanCustomConfigList = beanCustomConfigMapper.selectList(wrapper);
            beanCustomConfigList.forEach(c -> data.put(c.getName(), c.getDefaultValue()));
        }
        //取出chartVersion
        middleware.setChartVersion(values.getString("chart-version"));
        middleware.setChartName(config.getType());
        //记录当前数据
        Map<String, String> oldDate = new HashMap<>(data);
        // 更新配置，并记录是否重启
        boolean restart = false;
        for (CustomConfig customConfig : config.getCustomConfigList()) {
            if (customConfig.getRestart()) {
                restart = true;
            }
            //确认正则匹配
            if (!checkPattern(customConfig)) {
                log.error("集群{} 分区{} 中间件{} 参数{} 正则校验失败", config.getClusterId(), config.getNamespace(), config.getName(),
                    customConfig.getName());
                throw new CaasRuntimeException(ErrorMessage.VALIDATE_FAILED);
            }
            data.put(customConfig.getName(), customConfig.getValue());
        }
        updateValues(middleware, data, cluster, values);
        // 添加修改历史
        this.addCustomConfigHistory(config.getName(), oldDate, config);
        if (restart) {
            // 重启pod
            Middleware ware =
                podService.list(config.getClusterId(), config.getNamespace(), config.getName(), config.getType());
            ware.getPods().forEach(podInfo -> podService.restart(config.getClusterId(), config.getNamespace(),
                config.getName(), config.getType(), podInfo.getPodName()));
        }
    }

    @Override
    public List<CustomConfigHistoryDTO> getCustomConfigHistory(String clusterId, String namespace,
        String middlewareName, String type, String item, String startTime, String endTime) {
        QueryWrapper<BeanCustomConfigHistory> wrapper = new QueryWrapper<BeanCustomConfigHistory>()
            .eq("cluster_id", clusterId).eq("namespace", namespace).eq("name", middlewareName);
        List<BeanCustomConfigHistory> beanCustomConfigHistoryList = beanCustomConfigHistoryMapper.selectList(wrapper);

        // 筛选名称
        if (StringUtils.isNotEmpty(item)) {
            beanCustomConfigHistoryList = beanCustomConfigHistoryList.stream()
                .filter(
                    beanCustomConfigHistory -> StringUtils.containsIgnoreCase(beanCustomConfigHistory.getItem(), item))
                .collect(Collectors.toList());
        }
        // 过滤时间
        if (StringUtils.isNotEmpty(startTime) && StringUtils.isNotEmpty(endTime)) {
            Date start = DateUtils.addInteger(DateUtils.parseDate(startTime, DateUtils.YYYY_MM_DD_T_HH_MM_SS_Z),
                Calendar.HOUR_OF_DAY, -8);
            Date end = DateUtils.addInteger(DateUtils
                .addInteger(DateUtils.parseDate(endTime, DateUtils.YYYY_MM_DD_T_HH_MM_SS_Z), Calendar.DAY_OF_MONTH, 1),
                Calendar.HOUR_OF_DAY, -8);
            beanCustomConfigHistoryList = beanCustomConfigHistoryList.stream()
                .filter(beanCustomConfigHistory -> beanCustomConfigHistory.getDate().after(start)
                    && beanCustomConfigHistory.getDate().before(end))
                .collect(Collectors.toList());
        }
        List<CustomConfigHistoryDTO> customConfigHistoryDTOList = new ArrayList<>();
        beanCustomConfigHistoryList.forEach(beanCustomConfigHistory -> {
            CustomConfigHistoryDTO customConfigHistoryDTO = new CustomConfigHistoryDTO();
            BeanUtils.copyProperties(beanCustomConfigHistory, customConfigHistoryDTO);
            customConfigHistoryDTOList.add(customConfigHistoryDTO);
        });
        ThreadPoolExecutorFactory.executor
            .execute(() -> updateStatus(clusterId, namespace, middlewareName, type, customConfigHistoryDTOList));
        customConfigHistoryDTOList.sort(
            (o1, o2) -> o1.getDate() == null ? -1 : o2.getDate() == null ? -1 : o2.getDate().compareTo(o1.getDate()));
        return customConfigHistoryDTOList;
    }

    @Override
    public List<BeanCustomConfig> updateConfig2MySQL(HelmChartFile helmChartFile) throws Exception{
        QueryWrapper<BeanCustomConfig> wrapper = new QueryWrapper<BeanCustomConfig>()
                .eq("chart_name", helmChartFile.getChartName()).eq("chart_version", helmChartFile.getChartVersion());
        List<BeanCustomConfig> beanCustomConfigList = beanCustomConfigMapper.selectList(wrapper);
        return updateConfig2MySQL(helmChartFile, !CollectionUtils.isEmpty(beanCustomConfigList));
    }

    @Override
    public List<BeanCustomConfig> updateConfig2MySQL(HelmChartFile helmChartFile, Boolean update) throws Exception {
        try {
            JSONObject data = new JSONObject();
            for (String key : helmChartFile.getYamlFileMap().keySet()) {
                if ("parameters.yaml".equals(key)) {
                    Yaml yaml = new Yaml();
                    data = yaml.loadAs(helmChartFile.getYamlFileMap().get(key), JSONObject.class);
                }
            }
            // 转换为对象
            CustomConfigParameters parameters =
                JSONObject.parseObject(JSONObject.toJSONString(data), CustomConfigParameters.class);
            List<BeanCustomConfig> beanCustomConfigList = new ArrayList<>();
            parameters.getParameters().forEach(map -> {
                for (String key : map.keySet()) {
                    Map<String, String> param = map.get(key).stream()
                        .collect(Collectors.toMap(CustomConfigParameter::getName, CustomConfigParameter::getValue));
                    // 封装数据库对象
                    BeanCustomConfig beanCustomConfig = new BeanCustomConfig();
                    beanCustomConfig.setName(key);
                    beanCustomConfig.setDefaultValue(param.get("default"));
                    beanCustomConfig.setRestart("y".equals(param.get("isReboot")));
                    beanCustomConfig.setRanges(param.get("range"));
                    beanCustomConfig.setDescription(param.get("describe"));
                    beanCustomConfig.setChartName(helmChartFile.getChartName());
                    beanCustomConfig.setChartVersion(helmChartFile.getChartVersion());
                    if (param.containsKey("pattern")) {
                        beanCustomConfig.setPattern(param.get("pattern"));
                    }
                    if (update) {
                        QueryWrapper<BeanCustomConfig> wrapper =
                            new QueryWrapper<BeanCustomConfig>().eq("chart_name", beanCustomConfig.getChartName())
                                .eq("chart_version", beanCustomConfig.getChartVersion())
                                .eq("name", beanCustomConfig.getName());
                        beanCustomConfigMapper.update(beanCustomConfig, wrapper);
                    } else {
                        beanCustomConfigMapper.insert(beanCustomConfig);
                    }
                    beanCustomConfigList.add(beanCustomConfig);
                }
            });
            return beanCustomConfigList;
            // 存入数据库
        } catch (Exception e) {
            throw new CaasRuntimeException(ErrorMessage.MIDDLEWARE_UPDATE_MYSQL_CONFIG_FAILED);
        }
    }

    @Override
    public void deleteHistory(String clusterId, String namespace, String name) {
        QueryWrapper<BeanCustomConfigHistory> wrapper =
            new QueryWrapper<BeanCustomConfigHistory>().eq("cluster_id", clusterId)
                .eq("namespace", namespace).eq("name", name);
        beanCustomConfigHistoryMapper.delete(wrapper);
    }

    /**
     * 拉一个线程去更新是否已启用的状态
     */
    public void updateStatus(String clusterId, String namespace, String middlewareName, String type,
        List<CustomConfigHistoryDTO> customConfigHistoryDTOList) {
        customConfigHistoryDTOList.forEach(customConfigHistoryDTO -> {
            if (!customConfigHistoryDTO.getStatus()) {
                boolean status = true;
                if (customConfigHistoryDTO.getRestart()) {
                    // 获取pod列表
                    Middleware middleware = podService.list(clusterId, namespace, middlewareName, type);
                    for (PodInfo podInfo : middleware.getPods()) {
                        Date date =
                            DateUtils.addInteger(DateUtils.parseDate(StringUtils.isEmpty(podInfo.getLastRestartTime())
                                ? podInfo.getCreateTime() : podInfo.getLastRestartTime(),
                                DateUtils.YYYY_MM_DD_T_HH_MM_SS_Z), Calendar.HOUR_OF_DAY, 8);
                        if (customConfigHistoryDTO.getDate().after(date)) {
                            status = false;
                        }
                    }
                } else {
                    Date now = new Date();
                    status = DateUtils.getIntervalDays(now, customConfigHistoryDTO.getDate()) > 15;
                }
                if (status) {
                    BeanCustomConfigHistory beanCustomConfigHistory = new BeanCustomConfigHistory();
                    BeanUtils.copyProperties(customConfigHistoryDTO, beanCustomConfigHistory);
                    beanCustomConfigHistory.setStatus(true);
                    QueryWrapper<BeanCustomConfigHistory> wrapper =
                        new QueryWrapper<BeanCustomConfigHistory>().eq("id", beanCustomConfigHistory.getId());
                    beanCustomConfigHistoryMapper.update(beanCustomConfigHistory, wrapper);
                }
            }
        });
    }

    /**
     * 写入修改历史
     */
    public void addCustomConfigHistory(String middlewareName, Map<String, String> oldData,
        MiddlewareCustomConfig middlewareCustomConfig) {
        Date now = new Date();
        for (CustomConfig customConfig : middlewareCustomConfig.getCustomConfigList()) {
            BeanCustomConfigHistory beanCustomConfigHistory = new BeanCustomConfigHistory();
            beanCustomConfigHistory.setItem(customConfig.getName());
            beanCustomConfigHistory.setClusterId(middlewareCustomConfig.getClusterId());
            beanCustomConfigHistory.setNamespace(middlewareCustomConfig.getNamespace());
            beanCustomConfigHistory.setName(middlewareName);
            beanCustomConfigHistory.setLast(oldData.get(customConfig.getName()));
            beanCustomConfigHistory.setAfter(customConfig.getValue());
            beanCustomConfigHistory.setDate(now);
            beanCustomConfigHistory.setRestart(customConfig.getRestart());
            beanCustomConfigHistory.setStatus(false);
            beanCustomConfigHistoryMapper.insert(beanCustomConfigHistory);
        }
    }

    /**
     * 正则匹配
     */
    public boolean checkPattern(CustomConfig customConfig){
        if (StringUtils.isNotEmpty(customConfig.getPattern())){
            return Pattern.matches(customConfig.getPattern(), customConfig.getValue());
        }
        return true;
    }

    public Map<String, String> getConfigFromValues(Middleware middleware, JSONObject values){
        Map<String, String> data = new HashMap<>();
        if (values.containsKey("args")){
            JSONObject args = values.getJSONObject("args");
            for (String key : args.keySet()){
                data.put(key, args.getString(key));
            }
        }
        return data;
    }

    /**
     * 更新values.yaml
     */
    public void updateValues(Middleware middleware, Map<String, String> dataMap, MiddlewareClusterDTO cluster,
        JSONObject values) {
        JSONObject newValues = JSONObject.parseObject(values.toJSONString());
        JSONObject args = newValues.getJSONObject("args");
        if (args == null) {
            args = new JSONObject();
        }
        for (String key : dataMap.keySet()) {
            Pattern pattern = Pattern.compile("[0-9]*");
            Matcher isNum = pattern.matcher(dataMap.get(key));
            if (isNum.matches()) {
                args.put(key, Integer.parseInt(dataMap.get(key)));
            } else {
                args.put(key, dataMap.get(key));
            }
        }
        newValues.put("args", args);
        helmChartService.upgrade(middleware, values, newValues, cluster);
    }


}
