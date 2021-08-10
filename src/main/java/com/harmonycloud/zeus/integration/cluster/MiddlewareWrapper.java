package com.harmonycloud.zeus.integration.cluster;

import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.MIDDLEWARE_CLUSTER_GROUP;
import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.MIDDLEWARE_CLUSTER_VERSION;
import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.MIDDLEWARE_PLURAL;
import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.NAMESPACED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.alibaba.fastjson.JSONObject;
import com.harmonycloud.caas.common.enums.DictEnum;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareCRD;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareList;
import com.harmonycloud.zeus.util.K8sClient;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

/**
 * @author tangtx
 * @date 2021/03/26 11:00 AM
 * middleware cr
 */
@Component
public class MiddlewareWrapper {

    /**
     * crd的context
     */
    private static final CustomResourceDefinitionContext CONTEXT = new CustomResourceDefinitionContext.Builder()
            .withGroup(MIDDLEWARE_CLUSTER_GROUP)
            .withVersion(MIDDLEWARE_CLUSTER_VERSION)
            .withScope(NAMESPACED)
            .withPlural(MIDDLEWARE_PLURAL)
            .build();

    public List<MiddlewareCRD> list(String clusterId, String namespace, Map<String, String> labels) {
        if (StringUtils.isBlank(namespace)) {
            namespace = null;
        }
        if (CollectionUtils.isEmpty(labels)) {
            labels = null;
        }
        // 获取所有的集群资源
        Map<String, Object> map = K8sClient.getClient(clusterId).customResource(CONTEXT).list(namespace, labels);
        MiddlewareList middlewareList = JSONObject.parseObject(JSONObject.toJSONString(map), MiddlewareList.class);
        if (middlewareList == null || CollectionUtils.isEmpty(middlewareList.getItems())) {
            return new ArrayList<>(0);
        }
        return middlewareList.getItems();
    }

    public MiddlewareCRD get(String clusterId, String namespace, String name) {
        try {
            Map<String, Object> map = K8sClient.getClient(clusterId).customResource(CONTEXT).get(namespace, name);
            return JSONObject.parseObject(JSONObject.toJSONString(map), MiddlewareCRD.class);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                throw new BusinessException(DictEnum.MIDDLEWARE, name, ErrorMessage.NOT_EXIST);
            }
            throw e;
        }
    }

}
