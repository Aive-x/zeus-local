package com.harmonycloud.zeus.service.k8s.impl;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.enums.DictEnum;
import com.harmonycloud.caas.common.enums.ErrorCodeMessage;
import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.enums.Protocol;
import com.harmonycloud.caas.common.enums.middleware.StorageClassProvisionerEnum;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.caas.common.exception.CaasRuntimeException;
import com.harmonycloud.caas.common.model.middleware.*;
import com.harmonycloud.zeus.integration.cluster.ClusterWrapper;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareCluster;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareClusterInfo;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareClusterSpec;
import com.harmonycloud.zeus.service.k8s.ClusterCertService;
import com.harmonycloud.zeus.service.k8s.ClusterService;
import com.harmonycloud.zeus.service.k8s.NamespaceService;
import com.harmonycloud.zeus.service.k8s.NodeService;
import com.harmonycloud.zeus.service.log.EsComponentService;
import com.harmonycloud.zeus.service.registry.RegistryService;
import com.harmonycloud.zeus.util.K8sClient;
import com.harmonycloud.tool.date.DateUtils;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.harmonycloud.caas.common.constants.NameConstant.*;

/**
 * @author dengyulong
 * @date 2021/03/25
 */
@Slf4j
@Service
public class ClusterServiceImpl implements ClusterService {

    private static final Map<String, MiddlewareClusterDTO> CLUSTER_MAP = new ConcurrentHashMap<>();
    /**
     * 默认存储限额
     */
    private static final String DEFAULT_STORAGE_LIMIT = "100Gi";

    @Autowired
    private ClusterWrapper clusterWrapper;
    @Autowired
    private ClusterCertService clusterCertService;
    @Autowired
    private K8sClient k8sClient;
    @Autowired
    private RegistryService registryService;
    @Autowired
    private NodeService nodeService;
    @Autowired
    private NamespaceService namespaceService;
    @Autowired
    private EsComponentService esComponentService;
    @Autowired
    private ClusterService clusterService;

    @Value("${k8s.component.logging.es.user:elastic}")
    private String esUser;
    @Value("${k8s.component.logging.es.password:Hc@Cloud01}")
    private String esPassword;
    @Value("${k8s.component.logging.es.port:30092}")
    private String esPort;

    @Value("${k8s.component.crd.middlewareCrdYamlPath:/usr/local/zeus-pv/middleware-crd.yaml}")
    private String middlewareCrdYamlPath;

    @Override
    public List<MiddlewareClusterDTO> listClusters() {
        return listClusters(false);
    }

    @Override
    public List<MiddlewareClusterDTO> listClusters(boolean detail) {
        List<MiddlewareClusterDTO> clusters;
        if (CLUSTER_MAP.size() <= 0) {
            List<MiddlewareCluster> clusterList = clusterWrapper.listClusters();
            if (clusterList.size() <= 0) {
                return new ArrayList<>(0);
            }
            clusters = clusterList.stream().map(c -> {
                MiddlewareClusterInfo info = c.getSpec().getInfo();
                MiddlewareClusterDTO cluster = new MiddlewareClusterDTO();
                BeanUtils.copyProperties(info, cluster);
                cluster.setId(K8sClient.getClusterId(c.getMetadata())).setHost(info.getAddress())
                    .setName(c.getMetadata().getName()).setDcId(c.getMetadata().getNamespace())
                    .setIngress(info.getIngress());
                if (!CollectionUtils.isEmpty(c.getMetadata().getAnnotations())) {
                    cluster.setNickname(c.getMetadata().getAnnotations().get(NAME));
                }
                JSONObject attributes = new JSONObject();
                attributes.put(CREATE_TIME, DateUtils.parseUTCDate(c.getMetadata().getCreationTimestamp()));
                cluster.setAttributes(attributes);

                putIntoClusterMap(cluster);
                // 返回深拷贝对象
                return SerializationUtils.clone(cluster);
            }).collect(Collectors.toList());
        } else {
            // 深拷贝返回列表，避免影响内存
            clusters = new ArrayList<>();
            CLUSTER_MAP.values().forEach(dto -> clusters.add(SerializationUtils.clone(dto)));
        }

        // 返回命名空间信息
        if (detail && clusters.size() > 0) {
            clusters.parallelStream().forEach(cluster -> {
                // 初始化集群信息
                initClusterAttributes(cluster);
                try {
                    List<Namespace> list = namespaceService.list(cluster.getId());
                    cluster.getAttributes().put(NS_COUNT, list.size());
                } catch (Exception e) {
                    cluster.getAttributes().put(NS_COUNT, 0);
                    log.error("集群：{}，查询命名空间列表异常", cluster.getId(), e);
                }
            });
        }

        return clusters;
    }

    @Override
    public void initClusterAttributes(List<MiddlewareClusterDTO> clusters) {
        if (CollectionUtils.isEmpty(clusters)) {
            return;
        }
        clusters.parallelStream().forEach(this::initClusterAttributes);
    }

    @Override
    public void initClusterAttributes(MiddlewareClusterDTO cluster) {
        if (!CollectionUtils.isEmpty(cluster.getAttributes()) && cluster.getAttributes().get(KUBELET_VERSION) != null) {
            return;
        }
        nodeService.setClusterVersion(cluster);
        // 重新深拷贝个对象放入，避免参数传入的cluster被修改而导致map里的某些值为空
        putIntoClusterMap(SerializationUtils.clone(cluster));
    }

    @Override
    public MiddlewareClusterDTO findById(String clusterId) {
        MiddlewareClusterDTO dto = getFromClusterMap(clusterId);
        if (dto == null) {
            throw new CaasRuntimeException(ErrorMessage.CLUSTER_NOT_FOUND);
        }
        // 如果accessToken为空，尝试生成token
        if (StringUtils.isBlank(dto.getAccessToken())) {
            clusterCertService.generateTokenByCert(dto);
        }
        // 深拷贝对象返回，避免其他地方修改内容
        return SerializationUtils.clone(dto);
    }

    @Override
    public MiddlewareClusterDTO findByIdAndCheckRegistry(String clusterId) {
        MiddlewareClusterDTO cluster = findById(clusterId);
        if (cluster.getRegistry() == null || StringUtils.isBlank(cluster.getRegistry().getAddress())) {
            throw new IllegalArgumentException("harbor info is illegal");
        }
        return cluster;
    }

    @Override
    public void addCluster(MiddlewareClusterDTO cluster) {
        // 校验集群基本信息参数
        if (cluster == null || StringUtils.isAnyEmpty(cluster.getName(), cluster.getProtocol(), cluster.getHost())) {
            throw new IllegalArgumentException("cluster base info is null");
        }
        checkParams(cluster);

        // 校验集群基本信息
        // 校验集群是否已存在
        checkClusterExistent(cluster, false);
        cluster.setId(K8sClient.getClusterId(cluster));
        // 设置证书信息
        clusterCertService.setCertByAdminConf(cluster.getCert());

        // 校验registry
        registryService.validate(cluster.getRegistry());

        // 校验es
        if (StringUtils.isNotBlank(cluster.getLogging().getElasticSearch().getHost())
            && !esComponentService.checkEsConnection(cluster)) {
            throw new BusinessException(DictEnum.ES_COMPONENT, cluster.getLogging().getElasticSearch().getAddress(),
                ErrorMessage.VALIDATE_FAILED);
        }

        try {
            // 先添加fabric8客户端，否则无法用fabric8调用APIServer
            k8sClient.addK8sClient(cluster, false);
            K8sClient.getClient(cluster.getId()).namespaces().withName(DEFAULT).get();
        } catch (CaasRuntimeException ignore) {
        } catch (Exception e) {
            log.error("集群：{}，校验基本信息异常", cluster.getName(), e);
            // 移除fabric8客户端
            K8sClient.removeClient(cluster.getId());
            throw new BusinessException(DictEnum.CLUSTER, cluster.getName(), ErrorMessage.AUTH_FAILED);
        }
        // 保存集群
        MiddlewareCluster mw = convert(cluster);
        try {
            MiddlewareCluster c = clusterWrapper.create(mw);
            JSONObject attributes = new JSONObject();
            attributes.put(CREATE_TIME, DateUtils.parseUTCDate(c.getMetadata().getCreationTimestamp()));
            cluster.setAttributes(attributes);
        } catch (IOException e) {
            log.error("集群id：{}，添加集群异常", cluster.getId());
            throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.ADD_FAIL);
        }
        // 保存证书
        try {
            clusterCertService.saveCert(cluster);
        } catch (Exception e) {
            log.error("集群{}，保存证书异常", cluster.getId(), e);
        }

        // 放入map
        putIntoClusterMap(cluster);

        //创建middleware crd
        createMiddlewareCrd(cluster.getId());
    }

    @Override
    public void updateCluster(MiddlewareClusterDTO cluster) {
        // 校验集群基本信息参数
        if (StringUtils.isAnyEmpty(cluster.getNickname())) {
            throw new IllegalArgumentException("cluster nickname is null");
        }
        checkParams(cluster);

        // 校验集群基本信息
        // 校验集群是否已存在
        MiddlewareClusterDTO oldCluster = findById(cluster.getId());
        if (oldCluster == null) {
            throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.NOT_EXIST);
        }
        checkClusterExistent(cluster, true);
        // 设置证书信息
        clusterCertService.setCertByAdminConf(cluster.getCert());
        k8sClient.updateK8sClient(cluster);

        // 校验registry
        registryService.validate(cluster.getRegistry());

        // 校验es（包含重置es客户端）
        if (StringUtils.isNotBlank(cluster.getLogging().getElasticSearch().getHost())
            && (!esComponentService.checkEsConnection(cluster) || esComponentService.resetEsClient(cluster) == null)) {
            throw new BusinessException(DictEnum.ES_COMPONENT, cluster.getLogging().getElasticSearch().getAddress(),
                ErrorMessage.VALIDATE_FAILED);
        }

        // 只修改昵称，证书，ingress，制品服务，es
        oldCluster.setNickname(cluster.getNickname());
        oldCluster.setCert(cluster.getCert());
        oldCluster.setIngress(cluster.getIngress());
        oldCluster.setRegistry(cluster.getRegistry());
        oldCluster.setLogging(cluster.getLogging());

        update(oldCluster);
    }

    @Override
    public void update(MiddlewareClusterDTO cluster) {
        try {
            clusterWrapper.update(convert(cluster));
        } catch (IOException e) {
            log.error("集群{}的accessToken更新失败", cluster.getId());
            throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.UPDATE_FAIL);
        }
        // 放入map
        putIntoClusterMap(cluster);
    }

    private void checkParams(MiddlewareClusterDTO cluster) {
        if (cluster.getCert() == null || StringUtils.isEmpty(cluster.getCert().getCertificate())) {
            throw new IllegalArgumentException("cluster cert info is null");
        }

        // 校验集群使用的制品服务参数
        Registry registry = cluster.getRegistry();
        if (registry == null || StringUtils.isAnyEmpty(registry.getProtocol(), registry.getAddress(),
            registry.getChartRepo(), registry.getUser(), registry.getPassword())) {
            throw new IllegalArgumentException("cluster registry info is null");
        }

        // 校验集群使用的ingress信息
        if (cluster.getIngress() == null || StringUtils.isEmpty(cluster.getIngress().getAddress())) {
            throw new IllegalArgumentException("cluster ingress info is null");
        }

        // 设置默认参数
        // 如果没有数据中心，默认用default命名空间
        if (StringUtils.isBlank(cluster.getDcId())) {
            cluster.setDcId(DEFAULT);
        }
        // 给端口设置默认值，https是443，http是80
        if (cluster.getPort() == null) {
            cluster.setPort(cluster.getProtocol().equalsIgnoreCase(Protocol.HTTPS.getValue()) ? 443 : 80);
        }
        if (cluster.getRegistry().getPort() == null) {
            cluster.getRegistry()
                .setPort(cluster.getRegistry().getProtocol().equalsIgnoreCase(Protocol.HTTPS.getValue()) ? 443 : 80);
        }
        // 设置imageRepo
        if (StringUtils.isBlank(cluster.getRegistry().getImageRepo())) {
            cluster.getRegistry().setImageRepo(cluster.getRegistry().getChartRepo());
        }

        // 设置ingress
        if (cluster.getIngress().getTcp() == null) {
            cluster.getIngress().setTcp(new MiddlewareClusterIngress.IngressConfig());
        }

        // 设置es信息
        if (cluster.getLogging() == null) {
            cluster.setLogging(new MiddlewareClusterLogging());
        }
        if (cluster.getLogging().getElasticSearch() == null) {
            cluster.getLogging().setElasticSearch(new MiddlewareClusterLoggingInfo());
        }
        if (StringUtils.isNotEmpty(cluster.getLogging().getElasticSearch().getHost())) {
            if (StringUtils.isEmpty(cluster.getLogging().getElasticSearch().getProtocol())) {
                cluster.getLogging().getElasticSearch().setProtocol(Protocol.HTTP.getValue().toLowerCase());
            }
            if (StringUtils.isBlank(cluster.getLogging().getElasticSearch().getPort())) {
                cluster.getLogging().getElasticSearch().setPort(esPort);
            }
            if (StringUtils.isAnyBlank(cluster.getLogging().getElasticSearch().getUser(),
                cluster.getLogging().getElasticSearch().getPassword())) {
                cluster.getLogging().getElasticSearch().setUser(esUser).setPassword(esPassword);
            }
        }

        // 设置存储限额
        if (cluster.getStorage() == null) {
            cluster.setStorage(new HashMap<>());
        }
        if (cluster.getStorage().get(SUPPORT) == null) {
            List<String> defaultSupportList = StorageClassProvisionerEnum.getDefaultSupportType();
            Map<String, String> support =
                defaultSupportList.stream().collect(Collectors.toMap(s -> s, s -> DEFAULT_STORAGE_LIMIT));
            cluster.getStorage().put(SUPPORT, support);
        }
    }

    @Override
    public void removeCluster(String clusterId) {
        MiddlewareClusterDTO cluster = getFromClusterMap(clusterId);
        if (cluster == null) {
            return;
        }
        try {
            clusterWrapper.delete(cluster.getDcId(), cluster.getName());
        } catch (IOException e) {
            log.error("集群id：{}，删除集群异常", clusterId, e);
            throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.DELETE_FAIL);
        }
        // 从map中移除
        removeFromClusterMap(clusterId);
    }

    private MiddlewareClusterDTO getFromClusterMap(String clusterId) {
        return CLUSTER_MAP.get(clusterId);
    }

    private Collection<MiddlewareClusterDTO> listFromClusterMap() {
        return CLUSTER_MAP.values();
    }

    private void putIntoClusterMap(MiddlewareClusterDTO cluster) {
        CLUSTER_MAP.put(cluster.getId(), cluster);
    }

    private void removeFromClusterMap(String clusterId) {
        CLUSTER_MAP.remove(clusterId);
    }

    private void checkClusterExistent(MiddlewareClusterDTO cluster, boolean expectExisting) {
        // 校验内存中集群信息
        if (expectExisting) {
            // 期望集群存在 && 实际不存在
            if (getFromClusterMap(cluster.getId()) == null) {
                throw new BusinessException(DictEnum.CLUSTER, cluster.getName(), ErrorMessage.NOT_EXIST);
            }
            // 如果nickname重名
            if (listFromClusterMap().stream()
                .anyMatch(c -> !c.getId().equals(cluster.getId()) && c.getNickname().equals(cluster.getNickname()))) {
                throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.EXIST);
            }
        } else {
            // 获取所有集群
            Collection<MiddlewareClusterDTO> allClusters = listFromClusterMap();
            if (!CollectionUtils.isEmpty(allClusters)) {
                for (MiddlewareClusterDTO c : allClusters) {
                    // 集群名称
                    if (c.getId().equals(cluster.getId())) {
                        throw new BusinessException(DictEnum.CLUSTER, cluster.getName(), ErrorMessage.EXIST);
                    }
                    // 集群昵称
                    if (c.getNickname().equals(cluster.getNickname())) {
                        throw new BusinessException(DictEnum.CLUSTER, cluster.getNickname(), ErrorMessage.EXIST);
                    }
                    // APIServer地址
                    if (c.getHost().equals(cluster.getHost())) {
                        throw new BusinessException(DictEnum.CLUSTER, cluster.getAddress(), ErrorMessage.EXIST);
                    }
                }
            }
        }
    }

    private MiddlewareCluster convert(MiddlewareClusterDTO cluster) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(cluster.getName());
        meta.setNamespace(cluster.getDcId());
        Map<String, String> annotations = new HashMap<>();
        annotations.put(NAME, cluster.getNickname());
        meta.setAnnotations(annotations);
        MiddlewareClusterInfo clusterInfo = new MiddlewareClusterInfo();
        BeanUtils.copyProperties(cluster, clusterInfo);
        clusterInfo.setAddress(cluster.getHost());
        return new MiddlewareCluster().setMetadata(meta).setSpec(new MiddlewareClusterSpec().setInfo(clusterInfo));
    }

    private void createMiddlewareCrd(String clusterId) {
        MiddlewareClusterDTO middlewareClusterDTO = clusterService.findById(clusterId);

        boolean error = false;
        Process process = null;
        try {
            String execCommand;
            execCommand = MessageFormat.format(
                "kubectl create -f {0} --server={1} --token={2} --insecure-skip-tls-verify=true",
                middlewareCrdYamlPath, middlewareClusterDTO.getAddress(), middlewareClusterDTO.getAccessToken());
            log.info("执行kubectl命令：{}", execCommand);
            String[] commands = execCommand.split(" ");
            process = Runtime.getRuntime().exec(commands);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = stdInput.readLine()) != null) {
                log.info("执行指令执行成功:{}", line);
            }

            while ((line = stdError.readLine()) != null) {
                log.error("执行指令错误:{}", line);
                error = true;
            }
            if (error) {
                throw new Exception();
            }

        } catch (Exception e) {
            log.error("出现异常:", e);
            throw new CaasRuntimeException(String.valueOf(ErrorCodeMessage.RUN_COMMAND_ERROR));
        } finally {
            if (null != process) {
                process.destroy();
            }
        }
    }

}
