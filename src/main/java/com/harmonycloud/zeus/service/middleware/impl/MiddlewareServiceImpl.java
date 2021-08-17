package com.harmonycloud.zeus.service.middleware.impl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.harmonycloud.caas.common.constants.CommonConstant;
import com.harmonycloud.caas.common.model.middleware.*;
import com.harmonycloud.zeus.bean.BeanMiddlewareInfo;
import com.harmonycloud.zeus.integration.cluster.bean.MysqlReplicateCRD;
import com.harmonycloud.zeus.integration.cluster.bean.MysqlReplicateSpec;
import com.harmonycloud.zeus.integration.registry.bean.harbor.HelmListInfo;
import com.harmonycloud.zeus.schedule.MiddlewareManageTask;
import com.harmonycloud.zeus.service.k8s.*;
import com.harmonycloud.zeus.service.log.EsComponentService;
import com.harmonycloud.zeus.service.middleware.MiddlewareInfoService;
import com.harmonycloud.zeus.service.registry.HelmChartService;
import com.harmonycloud.tool.date.DateUtils;
import com.harmonycloud.tool.excel.ExcelUtil;
import com.harmonycloud.tool.page.PageObject;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareCRD;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.zeus.operator.BaseOperator;
import com.harmonycloud.zeus.service.AbstractBaseService;
import com.harmonycloud.zeus.service.middleware.MiddlewareService;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.MIDDLEWARE_EXPOSE_NODEPORT;

/**
 * @author dengyulong
 * @date 2021/03/23
 * 处理中间件
 */
@Slf4j
@Service
public class MiddlewareServiceImpl extends AbstractBaseService implements MiddlewareService {

    @Autowired
    private MiddlewareCRDService middlewareCRDService;
    @Autowired
    private ClusterService clusterService;
    @Autowired
    private MiddlewareManageTask middlewareManageTask;
    @Autowired
    private NamespaceService namespaceService;
    @Autowired
    private EsComponentService esComponentService;
    @Autowired
    private HelmChartService helmChartService;
    @Autowired
    private MiddlewareInfoService middlewareInfoService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    private IngressService ingressService;
    @Autowired
    private MysqlReplicateCRDService mysqlReplicateCRDService;

    private final static Map<String, String> titleMap = new HashMap<String, String>(7) {
        {
            put("0", "慢日志采集时间");
            put("1", "sql语句");
            put("2", "客户端IP");
            put("3", "执行时长(s)");
            put("4", "锁定时长(s)");
            put("5", "解析行数");
            put("6", "返回行数");
        }
    };

    @Override
    public List<Middleware> simpleList(String clusterId, String namespace, String type, String keyword) {
        MiddlewareClusterDTO cluster = clusterService.findById(clusterId);

        Map<String, String> label = null;
        List<String> nameList = new ArrayList<>();
        boolean nameFilter = false;
        if (StringUtils.isNotBlank(type) && MiddlewareTypeEnum.isType(type)) {
            label = new HashMap<>(1);
            label.put("type", MiddlewareTypeEnum.findByType(type).getMiddlewareCrdType());
        }
        else {
            nameList = getNameList(clusterId, namespace, type);
            nameFilter = true;
        }
        List<MiddlewareCRD> mwList = middlewareCRDService.listCR(clusterId, namespace, label);
        if (CollectionUtils.isEmpty(mwList)) {
            return new ArrayList<>(0);
        }
        //对于自定义中间件 根据名称进行过滤
        if (nameFilter) {
            if (CollectionUtils.isEmpty(nameList)) {
                return new ArrayList<>();
            }
            List<String> finalNameList = nameList;
            mwList = mwList.stream()
                .filter(mw -> finalNameList.stream().anyMatch(name -> mw.getSpec().getName().equals(name)))
                .collect(Collectors.toList());
        }

        // filter and convert
        Middleware middleware = new Middleware().setClusterId(clusterId).setNamespace(namespace).setType(type);
        Map<String, BaseOperator> operatorMap = new HashMap<>();
        boolean filter = StringUtils.isNotBlank(keyword);
        return mwList.stream().filter(mw -> !filter || mw.getMetadata().getName().contains(keyword)).map(mw -> {
            String middlewareType = MiddlewareTypeEnum.findTypeByCrdType(mw.getSpec().getType());
            if (!operatorMap.containsKey(middlewareType)) {
                middleware.setType(middlewareType);
                operatorMap.put(middlewareType, getOperator(BaseOperator.class, BaseOperator.class, middleware));
            }
            return operatorMap.get(middlewareType).convertByHelmChart(middlewareCRDService.simpleConvert(mw), cluster);
        }).collect(Collectors.toList());
    }

    @Override
    public Middleware detail(String clusterId, String namespace, String name, String type) {
        checkBaseParam(clusterId, namespace, name, type);
        Middleware middleware =
                new Middleware().setClusterId(clusterId).setNamespace(namespace).setType(type).setName(name);
        return getOperator(BaseOperator.class, BaseOperator.class, middleware).detail(middleware);
    }

    @Override
    public void create(Middleware middleware) {
        checkBaseParam(middleware);
        BaseOperator operator = getOperator(BaseOperator.class, BaseOperator.class, middleware);
        MiddlewareClusterDTO cluster = clusterService.findByIdAndCheckRegistry(middleware.getClusterId());
        // pre check
        operator.createPreCheck(middleware, cluster);
        // create
        middlewareManageTask.asyncCreate(middleware, cluster, operator);
        //创建灾备实例
        this.createDisasterRecoveryMiddleware(middleware);
    }

    @Override
    public void update(Middleware middleware) {
        checkBaseParam(middleware);
        BaseOperator operator = getOperator(BaseOperator.class, BaseOperator.class, middleware);
        MiddlewareClusterDTO cluster = clusterService.findByIdAndCheckRegistry(middleware.getClusterId());
        // pre check
        operator.updatePreCheck(middleware, cluster);
        // update
        middlewareManageTask.asyncUpdate(middleware, cluster, operator);
    }

    @Override
    public void delete(String clusterId, String namespace, String name, String type) {
        checkBaseParam(clusterId, namespace, name, type);
        Middleware middleware = new Middleware(clusterId, namespace, name, type);
        middlewareManageTask.asyncDelete(middleware, getOperator(BaseOperator.class, BaseOperator.class, middleware));
    }

    @Override
    public void switchMiddleware(String clusterId, String namespace, String name, String type, Boolean isAuto) {
        Middleware middleware = new Middleware(clusterId, namespace, name, type).setAutoSwitch(isAuto);
        middlewareManageTask.asyncSwitch(middleware, getOperator(BaseOperator.class, BaseOperator.class, middleware));
    }

    @Override
    public MonitorDto monitor(String clusterId, String namespace, String name, String type, String chartVersion) {
        Middleware middleware = new Middleware(clusterId, namespace, name, type);
        middleware.setChartVersion(chartVersion);
        return getOperator(BaseOperator.class, BaseOperator.class, middleware).monitor(middleware);
    }

    private void checkBaseParam(Middleware mw) {
        checkBaseParam(mw.getClusterId(), mw.getNamespace(), mw.getName(), mw.getType());
        if (StringUtils.isAnyBlank(mw.getChartName(), mw.getChartVersion())) {
            throw new IllegalArgumentException("chartName or chartVersion is null");
        }
    }

    private void checkBaseParam(String clusterId, String namespace, String name, String type) {
        if (StringUtils.isAnyBlank(clusterId, namespace, name, type)) {
            throw new IllegalArgumentException("middleware clusterId/namespace/name/type is null");
        }
    }

    @Override
    public List<Middleware> simpleListAll(String type) {

        List<MiddlewareClusterDTO> clusterList = clusterService.listClusters();
        List<Middleware> list = new ArrayList<>();
        clusterList.forEach(cluster -> {
            List<Namespace> namespaceList = namespaceService.list(cluster.getId(), true, null);
            namespaceList = namespaceList.stream().filter(Namespace::isRegistered).collect(Collectors.toList());
            namespaceList.forEach(namespace -> {
                List<Middleware> mwList = middlewareCRDService.list(cluster.getId(), namespace.getName(), type);
                list.addAll(mwList);
            });
        });
        return list;

    }

    @Override
    public PageObject<MysqlSlowSqlDTO> slowsql(SlowLogQuery slowLogQuery) throws Exception {
        MiddlewareClusterDTO cluster = clusterService.findById(slowLogQuery.getClusterId());
        PageObject<MysqlSlowSqlDTO> slowSqlDTOS = esComponentService.getSlowSql(cluster, slowLogQuery);
        return slowSqlDTOS;
    }

    @Override
    public void slowsqlExcel(SlowLogQuery slowLogQuery, HttpServletResponse response, HttpServletRequest request) throws Exception {
        slowLogQuery.setCurrent(1);
        slowLogQuery.setSize(CommonConstant.NUM_ONE_THOUSAND);
        PageObject<MysqlSlowSqlDTO> slowsql = slowsql(slowLogQuery);
        List<Map<String, Object>> demoValues = new ArrayList<>();
        slowsql.getData().stream().forEach(mysqlSlowSqlDTO -> {
            Map<String, Object> demoValue = new HashMap<String, Object>() {
                {
                    Date queryDate = DateUtils.parseUTCSDate(mysqlSlowSqlDTO.getTimestampMysql());
                    put("0", queryDate);
                    put("1", mysqlSlowSqlDTO.getQuery());
                    put("2", mysqlSlowSqlDTO.getClientip());
                    put("3", mysqlSlowSqlDTO.getQueryTime());
                    put("4", mysqlSlowSqlDTO.getLockTime());
                    put("5", mysqlSlowSqlDTO.getRowsExamined());
                    put("6", mysqlSlowSqlDTO.getRowsSent());
                }
            };
            demoValues.add(demoValue);
        });
        ExcelUtil.writeExcel(ExcelUtil.OFFICE_EXCEL_XLSX, "mysqlslowsql", null, titleMap, demoValues, response, request);
    }
    
    public List<String> getNameList(String clusterId, String namespace, String type) {
        // 获取中间件chartName + chartVersion
        List<BeanMiddlewareInfo> mwInfoList = middlewareInfoService.list(clusterId);
        mwInfoList =
            mwInfoList.stream().filter(mwInfo -> mwInfo.getChartName().equals(type)).collect(Collectors.toList());
        List<String> chartList = mwInfoList.stream()
            .map(mwInfo -> mwInfo.getChartName() + "-" + mwInfo.getChartVersion()).collect(Collectors.toList());
        // 获取helm list信息
        List<HelmListInfo> helmInfoList = helmChartService.listHelm(namespace, "", clusterService.findById(clusterId));
        helmInfoList = helmInfoList.stream()
            .filter(helmInfo -> chartList.stream().allMatch(chart -> chart.equals(helmInfo.getChart())))
            .collect(Collectors.toList());
        return helmInfoList.stream().map(HelmListInfo::getName).collect(Collectors.toList());
    }

    /**
     * 创建灾备实例
     * @param middleware
     */
    public void createDisasterRecoveryMiddleware(Middleware middleware) {
        MysqlDTO mysqlDTO = middleware.getMysqlDTO();
        if (mysqlDTO.getOpenDisasterRecoveryMode() != null && mysqlDTO.getOpenDisasterRecoveryMode()) {
            //1.为实例创建对外服务(NodePort)
            middlewareManageTask.asyncCreateNodePortService(middleware, this);
            //2.设置灾备实例信息，创建灾备实例
            //2.1 设置灾备实例信息
            Middleware relationMiddleware = middleware.getRelationMiddleware();
            relationMiddleware.setClusterId(mysqlDTO.getRelationClusterId());
            relationMiddleware.setNamespace(mysqlDTO.getRelationNamespace());
            relationMiddleware.setName(mysqlDTO.getRelationName());
            relationMiddleware.setAliasName(mysqlDTO.getRelationAliasName());

            //2.2 给灾备实例设置源实例信息
            MysqlDTO sourceDto = new MysqlDTO();
            sourceDto.setRelationClusterId(middleware.getClusterId());
            sourceDto.setRelationNamespace(middleware.getNamespace());
            sourceDto.setRelationName(middleware.getName());
            sourceDto.setRelationAliasName(middleware.getAliasName());
            sourceDto.setReplicaCount(middleware.getMysqlDTO().getReplicaCount());
            sourceDto.setOpenDisasterRecoveryMode(true);
            sourceDto.setIsSource(false);
            sourceDto.setType("slave-slave");
            relationMiddleware.setMysqlDTO(sourceDto);

            BaseOperator operator = getOperator(BaseOperator.class, BaseOperator.class, relationMiddleware);
            MiddlewareClusterDTO cluster = clusterService.findByIdAndCheckRegistry(relationMiddleware.getClusterId());
            operator.createPreCheck(relationMiddleware, cluster);
            middlewareManageTask.asyncCreate(relationMiddleware, cluster, operator);
            //3.异步创建关联关系
            middlewareManageTask.asyncCreateMysqlReplicate(middleware, relationMiddleware, this);
        }
    }

    /**
     * 创建对外服务(NodePort)
     * @param middleware
     */
    public void createOpenService(Middleware middleware){
        //1.获取所有服务
        List<ServicePortDTO> servicePortDTOS = serviceService.list(middleware.getClusterId(), middleware.getNamespace(), middleware.getName(), middleware.getType());
        List<ServicePortDTO> readonlyServiceList = servicePortDTOS.stream().filter(servicePortDTO -> servicePortDTO.getServiceName().endsWith("readonly")).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(readonlyServiceList)) {
            ServicePortDTO servicePortDTO = readonlyServiceList.get(0);
            PortDetailDTO portDetailDTO = servicePortDTO.getPortDetailDtoList().get(0);
            //2.将readonly服务通过NodePort暴露为对外服务
            boolean successCreateService = false;
            int servicePort = 31000;
            while (!successCreateService) {
                log.info("开始创建对外服务,clusterId={},namespace={},middlewareName={},port={}",
                        middleware.getClusterId(), middleware.getNamespace(), middleware.getName(), servicePort);
                try {
                    IngressDTO ingressDTO = new IngressDTO();
                    List<ServiceDTO> serviceList = new ArrayList<>();
                    ServiceDTO serviceDTO = new ServiceDTO();
                    serviceDTO.setExposePort(String.valueOf(servicePort));
                    serviceDTO.setTargetPort(portDetailDTO.getTargetPort());
                    serviceDTO.setServicePort(portDetailDTO.getPort());
                    serviceDTO.setServiceName(servicePortDTO.getServiceName());
                    serviceList.add(serviceDTO);

                    ingressDTO.setMiddlewareType(MiddlewareTypeEnum.MYSQL.getType());
                    ingressDTO.setServiceList(serviceList);
                    ingressDTO.setExposeType(MIDDLEWARE_EXPOSE_NODEPORT);
                    ingressService.create(middleware.getClusterId(), middleware.getNamespace(), middleware.getName(), ingressDTO);
                    successCreateService = true;
                    log.info("对外服务创建成功");
                } catch (Exception e) {
                    servicePort++;
                    log.error("对外服务创建失败，尝试端口：{}", servicePort, e);
                    successCreateService = false;
                }
            }
        }
    }

    /**
     * 创建源实例和灾备实例的关联关系
     * @param original
     */
    public void createMysqlReplicate(Middleware original, Middleware disasterRecovery) {
        Middleware middleware = this.detail(original.getClusterId(), original.getNamespace(), original.getName(), original.getType());
        List<IngressDTO> ingressDTOS = ingressService.get(original.getClusterId(), middleware.getNamespace(),
                middleware.getType(), middleware.getName());
        log.info("准备创建MysqlReplicate,middleware={},ingressDTOS={}", middleware, ingressDTOS);
        if (!CollectionUtils.isEmpty(ingressDTOS)) {
            List<IngressDTO> readonlyIngressDTOList = ingressDTOS.stream().filter(ingressDTO -> (
                    ingressDTO.getName().contains("readonly") && ingressDTO.getExposeType().equals(MIDDLEWARE_EXPOSE_NODEPORT))
            ).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(readonlyIngressDTOList)) {
                IngressDTO ingressDTO = readonlyIngressDTOList.get(0);
                List<ServiceDTO> serviceList = ingressDTO.getServiceList();
                if (!CollectionUtils.isEmpty(serviceList)) {
                    ServiceDTO serviceDTO = serviceList.get(0);
                    MysqlReplicateSpec spec = new MysqlReplicateSpec(true, disasterRecovery.getName(),
                            ingressDTO.getExposeIP(), Integer.parseInt(serviceDTO.getExposePort()), "root", middleware.getPassword());

                    MysqlReplicateCRD mysqlReplicateCRD = new MysqlReplicateCRD();
                    ObjectMeta metaData = new ObjectMeta();
                    metaData.setName(disasterRecovery.getName());
                    metaData.setNamespace(disasterRecovery.getNamespace());
                    Map<String, String> labels = new HashMap<>();
                    labels.put("operatorname", "mysql-operator");
                    metaData.setLabels(labels);

                    mysqlReplicateCRD.setSpec(spec);
                    mysqlReplicateCRD.setMetadata(metaData);
                    mysqlReplicateCRD.setKind("MysqlReplicate");

                    try {
                        log.info("创建mysql实例 {} 和 {} 的关联关系MysqlReplicate", original.getName(), middleware.getName());
                        mysqlReplicateCRDService.createOrReplaceMysqlReplicate(disasterRecovery.getClusterId(), mysqlReplicateCRD);
                        log.info("MysqlReplicate创建成功");
                    } catch (IOException e) {
                        log.error("MysqlReplicate创建失败", e);
                        e.printStackTrace();
                    }
                }
            }
        }else{
            log.info("未找到只读服务，无法创建MysqlReplicate");
        }
    }
}
