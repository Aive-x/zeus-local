package com.harmonycloud.zeus.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.harmonycloud.zeus.service.k8s.ClusterCertService;
import com.harmonycloud.zeus.service.k8s.ClusterService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.exception.CaasRuntimeException;
import com.harmonycloud.caas.common.model.middleware.MiddlewareClusterDTO;
import com.harmonycloud.tool.file.FileUtil;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

/**
 * @author dengyulong
 * @date 2020/03/26
 */
@Slf4j
@Component
public class K8sClient {

    private static final Map<String, KubernetesClient> K8S_CLIENT_MAP = new ConcurrentHashMap<>();

    private static String url;

    private static String token;

    public static final String DEFAULT_CLIENT = "defaultClient";

    @Autowired
    private ClusterService clusterService;
    @Autowired
    private ClusterCertService clusterCertService;

    @Value("${k8s.token.path:/run/secrets/kubernetes.io/serviceaccount/token}")
    private void setToken(String tokenPath) {
        if (StringUtils.isBlank(token)) {
            try {
                K8sClient.token = FileUtil.readFile(tokenPath);
            } catch (IOException e) {
                log.error("获取serviceaccount的token异常", e);
            }
        }
    }

    public static String getClusterId(MiddlewareClusterDTO cluster) {
        return cluster.getDcId() + "--" + cluster.getName();
    }

    /**
     * 获取默认的client
     */
    public static KubernetesClient getDefaultClient() {
        return InstanceHolder.KUBERNETES_CLIENT;
    }

    /**
     * 获取client
     */
    public static KubernetesClient getClient(String clusterId) {
        KubernetesClient client = K8S_CLIENT_MAP.get(clusterId);
        if (client == null) {
            throw new CaasRuntimeException(ErrorMessage.CLUSTER_NOT_FOUND);
        }
        return client;
    }

    /**
     * 初始化
     */
    public void initClients() {
        List<MiddlewareClusterDTO> middlewareClusters = clusterService.listClusters();
        if (middlewareClusters.size() > 0) {
            addK8sClients(middlewareClusters);
            clusterService.initClusterAttributes(middlewareClusters);
        }
    }

    /**
     * 添加k8s客户端
     */
    public void addK8sClient(MiddlewareClusterDTO c) {
        addK8sClient(c, true);
    }

    /**
     * 添加k8s客户端
     */
    public void addK8sClients(List<MiddlewareClusterDTO> middlewareClusters) {
        middlewareClusters.forEach(c -> {
            if (K8S_CLIENT_MAP.containsKey(c.getId())) {
                return;
            }
            KubernetesClient client;
            if (StringUtils.isNotEmpty(c.getCert().getCertificateAuthorityData())) {
                // 使用证书构建client
                client = new DefaultKubernetesClient(new ConfigBuilder()
                    .withMasterUrl(c.getAddress())
                    .withTrustCerts(true)
                    .withCaCertData(c.getCert().getCertificateAuthorityData())
                    .withClientCertData(c.getCert().getClientCertificateData())
                    .withClientKeyData(c.getCert().getClientKeyData())
                    // 需将 Namespace 初始化为 null
                    .withNamespace(null)
                    .build());
            } else {
                // 使用token构建client，会解析成证书，所以优先使用证书
                client = new DefaultKubernetesClient(new ConfigBuilder().withMasterUrl(c.getAddress())
                    .withTrustCerts(true).withOauthToken(c.getAccessToken()).build());
            }
            K8S_CLIENT_MAP.put(c.getId(), client);

            // 保存证书
            try {
                clusterCertService.saveCert(c);
            } catch (Exception e) {
                log.error("集群{}保存证书异常", c.getId());
            }
        });
    }

    /**
     * 添加k8s客户端
     */
    public void addK8sClient(MiddlewareClusterDTO c, boolean initCert) {
        if (K8S_CLIENT_MAP.containsKey(c.getId())) {
            return;
        }
        KubernetesClient client;
        if (StringUtils.isNotEmpty(c.getCert().getCertificateAuthorityData())) {
            // 使用证书构建client
            client = new DefaultKubernetesClient(new ConfigBuilder()
                .withMasterUrl(c.getAddress())
                .withTrustCerts(true)
                .withCaCertData(c.getCert().getCertificateAuthorityData())
                .withClientCertData(c.getCert().getClientCertificateData())
                .withClientKeyData(c.getCert().getClientKeyData())
                // 需将 Namespace 初始化为 null
                .withNamespace(null)
                .build());
        } else {
            // 使用token构建client，会解析成证书，所以优先使用证书
            client = new DefaultKubernetesClient(new ConfigBuilder().withMasterUrl(c.getAddress()).withTrustCerts(true)
                .withOauthToken(c.getAccessToken()).build());
        }
        K8S_CLIENT_MAP.put(c.getId(), client);

        if (initCert) {
            // 保存证书
            try {
                clusterCertService.saveCert(c);
            } catch (Exception e) {
                log.error("集群{}，保存证书异常", c.getId(), e);
            }
        }
    }

    /**
     * 移除k8s客户端
     *
     * @param clusterId 集群id
     */
    public static void removeClient(String clusterId) {
        K8S_CLIENT_MAP.remove(clusterId);
    }

    public static String getClusterId(ObjectMeta metadata) {
        return metadata.getNamespace() + "--" + metadata.getName();
    }

    /**
     * 修改k8s客户端
     */
    public void updateK8sClient(MiddlewareClusterDTO c) {
        KubernetesClient client;
        if (StringUtils.isNotEmpty(c.getCert().getCertificateAuthorityData())) {
            // 使用证书构建client
            client = new DefaultKubernetesClient(new ConfigBuilder()
                .withMasterUrl(c.getAddress())
                .withTrustCerts(true)
                .withCaCertData(c.getCert().getCertificateAuthorityData())
                .withClientCertData(c.getCert().getClientCertificateData())
                .withClientKeyData(c.getCert().getClientKeyData())
                // 需将 Namespace 初始化为 null
                .withNamespace(null)
                .build());
        } else {
            // 使用token构建client，会解析成证书，所以优先使用证书
            client = new DefaultKubernetesClient(new ConfigBuilder().withMasterUrl(c.getAddress()).withTrustCerts(true)
                .withOauthToken(c.getAccessToken()).build());
        }
        K8S_CLIENT_MAP.put(c.getId(), client);

        // 保存证书
        try {
            clusterCertService.saveCert(c);
        } catch (Exception e) {
            log.error("集群{}保存证书异常", c.getId());
        }
    }

    /**
     * 内部类来保证单例的线程安全
     */
    private static class InstanceHolder {
        private static final KubernetesClient KUBERNETES_CLIENT = new DefaultKubernetesClient(new ConfigBuilder()
            .withMasterUrl(url).withTrustCerts(true).withOauthToken(token).withNamespace(null).build());

        static {
            K8S_CLIENT_MAP.put(DEFAULT_CLIENT, KUBERNETES_CLIENT);
        }
    }

    public static String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        K8sClient.url = url;
    }
}
