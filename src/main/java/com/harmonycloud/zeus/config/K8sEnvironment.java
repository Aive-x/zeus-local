package com.harmonycloud.zeus.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.stereotype.Component;

import com.harmonycloud.zeus.util.K8sClient;

/**
 * @author dengyulong
 * @date 2020/06/05
 * 读取环境变量
 */
@Component
public class K8sEnvironment implements EnvironmentAware {

    @Autowired
    private K8sClient k8sClient;
    @Value("${k8s.service.host:kubernetes}")
    private String defaultK8sServiceHost;
    @Value("${k8s.service.port:443}")
    private String defaultK8sServicePort;

    @Override
    public void setEnvironment(org.springframework.core.env.Environment environment) {
        // 先取环境变量
        String k8sServiceHost = environment.getProperty("KUBERNETES_SERVICE_HOST");
        String k8sServicePort = environment.getProperty("KUBERNETES_SERVICE_PORT");

        if (StringUtils.isBlank(k8sServiceHost)) {
            // svc的名称
            k8sServiceHost = defaultK8sServiceHost;
        }
        if (StringUtils.isBlank(k8sServicePort)) {
            // 默认端口
            k8sServicePort = defaultK8sServicePort;
        }
        String k8sUrl = "https://" + k8sServiceHost + ":" + k8sServicePort;
        k8sClient.setUrl(k8sUrl);

        // 初始化所有集群的客户端
        k8sClient.initClients();
    }

}
