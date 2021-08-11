package com.harmonycloud.zeus.operator.impl;

import static com.harmonycloud.caas.common.constants.NameConstant.CLUSTER;
import static com.harmonycloud.caas.common.constants.NameConstant.MODE;
import static com.harmonycloud.caas.common.constants.NameConstant.RESOURCES;

import com.alibaba.fastjson.JSONArray;
import com.harmonycloud.caas.common.model.middleware.*;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSONObject;
import com.harmonycloud.caas.common.enums.middleware.RocketMQModeEnum;
import com.harmonycloud.zeus.annotation.Operator;
import com.harmonycloud.zeus.operator.api.MqOperator;
import com.harmonycloud.zeus.operator.miiddleware.AbstractMqOperator;

import java.util.*;

/**
 * @author dengyulong
 * @date 2021/03/23
 * 处理mq逻辑
 */
@Operator(paramTypes4One = Middleware.class)
public class MqOperatorImpl extends AbstractMqOperator implements MqOperator {

    @Override
    public void replaceValues(Middleware middleware, MiddlewareClusterDTO cluster, JSONObject values) {
        // 替换通用的值
        replaceCommonValues(middleware, cluster, values);
        // 把通用里设置的mode去掉，mq不用在第一级设置mode
        values.remove(MODE);
        MiddlewareQuota mqQuota = middleware.getQuota().get(middleware.getType());
        replaceCommonResources(mqQuota, values.getJSONObject(RESOURCES));
        replaceCommonStorages(mqQuota, values);

        //替换ACL认证参数
        replaceACL(middleware, values);

        // 资源配额
        JSONObject clusterInfo = values.getJSONObject(CLUSTER);
        clusterInfo.put(MODE, middleware.getMode());
        RocketMQModeEnum modeEnum = RocketMQModeEnum.findByMode(middleware.getMode());
        switch (modeEnum) {
            case TWO_MASTER:
                clusterInfo.put("allMaster", true);
                clusterInfo.put("replacesCount", 2);
                clusterInfo.put("membersPerGroup", 1);
                clusterInfo.put("groupReplica", 2);
                break;
            case TWO_MASTER_TWO_SLAVE:
                clusterInfo.put("allMaster", false);
                clusterInfo.put("replacesCount", 4);
                clusterInfo.put("membersPerGroup", 2);
                clusterInfo.put("groupReplica", 2);
                break;
            case THREE_MASTER_THREE_SLAVE:
                clusterInfo.put("allMaster", false);
                clusterInfo.put("replacesCount", 6);
                clusterInfo.put("membersPerGroup", 2);
                clusterInfo.put("groupReplica", 3);
                break;
            default:
        }

        // jvm堆内存
        String mem = calculateMem(mqQuota.getLimitMemory(), "0.5", "m");
        JSONObject javaOpts = values.getJSONObject("javaOpts");
        javaOpts.put("xms", mem);
        javaOpts.put("xmx", mem);
    }

    @Override
    public Middleware convertByHelmChart(Middleware middleware, MiddlewareClusterDTO cluster) {
        JSONObject values = helmChartService.getInstalledValues(middleware, cluster);
        convertCommonByHelmChart(middleware, values);
        convertStoragesByHelmChart(middleware, middleware.getType(), values);

        // 处理mq特有参数
        if (values != null) {
            convertResourcesByHelmChart(middleware, middleware.getType(), values.getJSONObject(RESOURCES));

            JSONObject clusterInfo = values.getJSONObject(CLUSTER);
            middleware.setMode(clusterInfo.getString(MODE));
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
            // 实例规格扩容
            // cpu
            if (StringUtils.isNotBlank(quota.getCpu())) {
                sb.append("resources.requests.cpu=").append(quota.getCpu()).append(",resources.limits.cpu=")
                    .append(quota.getLimitCpu()).append(",");
            }
            // memory
            if (StringUtils.isNotBlank(quota.getMemory())) {
                sb.append("resources.requests.memory=").append(quota.getMemory()).append(",resources.limits.memory=")
                    .append(quota.getLimitMemory()).append(",");
                // 计算jvm堆内存
                String mem = calculateMem(quota.getLimitMemory(), "0.5", "m");
                sb.append("javaOpts.xms=").append(mem).append(",javaOpts.xmx=").append(mem).append(",");
            }

            // 实例模式扩容
            if (StringUtils.isNotBlank(middleware.getMode())) {
                sb.append("cluster.mode=").append(middleware.getMode()).append(",");
                RocketMQModeEnum modeEnum = RocketMQModeEnum.findByMode(middleware.getMode());
                switch (modeEnum) {
                    case TWO_MASTER:
                        sb.append("cluster.allMaster=").append(true).append(",cluster.replacesCount=").append(2)
                            .append(",cluster.membersPerGroup=").append(1).append(",cluster.groupReplica=").append(2)
                            .append(",");
                        break;
                    case TWO_MASTER_TWO_SLAVE:
                        sb.append("cluster.allMaster=").append(false).append(",cluster.replacesCount=").append(4)
                            .append(",cluster.membersPerGroup=").append(2).append(",cluster.groupReplica=").append(2)
                            .append(",");
                        break;
                    case THREE_MASTER_THREE_SLAVE:
                        sb.append("cluster.allMaster=").append(false).append(",cluster.replacesCount=").append(6)
                            .append(",cluster.membersPerGroup=").append(2).append(",cluster.groupReplica=").append(3)
                            .append(",");
                        break;
                    default:
                }
            }
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
    public List<String> getConfigmapDataList(ConfigMap configMap) {
        return new ArrayList<>(Arrays.asList(configMap.getData().get("broker.properties.tmpl").split("\n")));
    }

    /**
     * 转换data为map形式
     */
    @Override
    public Map<String, String> configMap2Data(ConfigMap configMap) {
        String dataString = configMap.getData().get("broker.properties.tmpl");
        Map<String, String> dataMap = new HashMap<>();
        String[] datalist = dataString.split("\n");
        for (String data : datalist) {
            if (data.contains("#") || StringUtils.isEmpty(data)) {
                continue;
            }
            String[] keyValue = data.split("=");
            dataMap.put(keyValue[0], keyValue[1]);
        }
        return dataMap;
    }

    @Override
    public void editConfigMapData(CustomConfig customConfig, List<String> data){
        for (int i = 0; i < data.size(); ++i) {
            if (data.get(i).contains(customConfig.getName())) {
                String temp = StringUtils.substring(data.get(i), data.get(i).indexOf("=") + 1, data.get(i).length());
                if (data.get(i).replace(" ", "").replace(temp, "").replace("=", "").equals(customConfig.getName())){
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
        configMap.getData().put("broker.properties.tmpl", temp.toString());
    }

    public void replaceACL(Middleware middleware, JSONObject values) {
        JSONObject acl = new JSONObject();
        if (middleware.getRocketMQParam().getAcl().getEnable()) {
            RocketMQACL rocketMQACL = middleware.getRocketMQParam().getAcl();
            acl.put("enable", rocketMQACL.getEnable());
            acl.put("globalWhiteRemoteAddresses",
                    new JSONArray(Arrays.asList(rocketMQACL.getGlobalWhiteRemoteAddresses().split(";"))));
            JSONArray accounts = new JSONArray();
            for (RocketMQAccount mqAccount : rocketMQACL.getRocketMQAccountList()) {
                JSONObject account = new JSONObject();
                account.put("accessKey", mqAccount.getAccessKey());
                account.put("secretKey", mqAccount.getSecretKey());
                account.put("whiteRemoteAddress", mqAccount.getWhiteRemoteAddress());
                account.put("admin", mqAccount.getAdmin());
                account.put("defaultTopicPerm", mqAccount.getTopicPerms().get("defaultTopicPerm"));
                account.put("defaultGroupPerm", mqAccount.getGroupPerms().get("defaultGroupPerm"));

                JSONArray topicPerms = new JSONArray();
                for (String key : mqAccount.getTopicPerms().keySet()) {
                    if ("defaultTopicPerm".equals(key)) {
                        continue;
                    }
                    topicPerms.add(key + "=" + mqAccount.getTopicPerms().get(key));
                }
                account.put("topicPerms", topicPerms);

                JSONArray groupPerms = new JSONArray();
                for (String key : mqAccount.getGroupPerms().keySet()) {
                    if ("defaultGroupPerm".equals(key)) {
                        continue;
                    }
                    groupPerms.add(key + "=" + mqAccount.getGroupPerms().get(key));
                }
                account.put("groupPerms", groupPerms);
                accounts.add(account);
            }
            acl.put("accounts", accounts);
        } else {
            acl.put("enable", false);
        }
        values.put("acl", acl);
    }
}
