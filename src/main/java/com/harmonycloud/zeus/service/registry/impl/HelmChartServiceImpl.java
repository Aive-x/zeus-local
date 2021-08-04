package com.harmonycloud.zeus.service.registry.impl;

import static com.harmonycloud.caas.common.constants.registry.HelmChartConstant.CHART_YAML_NAME;
import static com.harmonycloud.caas.common.constants.registry.HelmChartConstant.TEMPLATES;
import static com.harmonycloud.caas.common.constants.registry.HelmChartConstant.VALUES_YAML_NAME;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.harmonycloud.caas.common.exception.CaasRuntimeException;
import com.harmonycloud.caas.common.model.middleware.QuestionYaml;
import com.harmonycloud.zeus.bean.BeanMiddlewareInfo;
import com.harmonycloud.zeus.service.k8s.ClusterService;
import com.harmonycloud.zeus.service.middleware.MiddlewareInfoService;
import com.harmonycloud.zeus.service.middleware.MiddlewareService;
import com.harmonycloud.zeus.integration.registry.HelmChartWrapper;
import com.harmonycloud.zeus.integration.registry.bean.harbor.HelmListInfo;
import com.harmonycloud.zeus.integration.registry.bean.harbor.V1HelmChartVersion;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson.JSONObject;

import com.harmonycloud.caas.common.enums.ErrorMessage;
import com.harmonycloud.caas.common.enums.registry.RegistryType;
import com.harmonycloud.caas.common.exception.BusinessException;
import com.harmonycloud.caas.common.model.middleware.Middleware;
import com.harmonycloud.caas.common.model.middleware.MiddlewareClusterDTO;
import com.harmonycloud.caas.common.model.middleware.Registry;
import com.harmonycloud.caas.common.model.registry.HelmChartFile;
import com.harmonycloud.zeus.service.k8s.ClusterCertService;
import com.harmonycloud.zeus.service.registry.AbstractRegistryService;
import com.harmonycloud.zeus.service.registry.HelmChartService;
import com.harmonycloud.tool.cmd.CmdExecUtil;
import com.harmonycloud.tool.cmd.HelmChartUtil;
import com.harmonycloud.tool.file.FileUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * @author dengyulong
 * @date 2021/03/29
 */
@Slf4j
@Service
public class HelmChartServiceImpl extends AbstractRegistryService implements HelmChartService {

    /**
     * helm chart的上传子目录
     */
    private static final String SUB_DIR = "/helmcharts/";

    @Autowired
    private HelmChartWrapper helmChartWrapper;
    @Autowired
    private ClusterCertService clusterCertService;
    @Autowired
    private ClusterService clusterService;
    @Autowired
    private MiddlewareService middlewareService;
    @Autowired
    private MiddlewareInfoService middlewareInfoService;

    @Value("${system.upload.path:/usr/local/zeus-pv/upload}")
    private String uploadPath;

    @Override
    public List<V1HelmChartVersion> listHelmChartVersions(Registry registry, String chartName) {
        return helmChartWrapper.listHelmChartVersions(registry, chartName);
    }

    @Override
    public HelmChartFile getHelmChartFromRegistry(Registry registry, String chartName, String chartVersion) {
        String tgzFilePath = getTgzFilePath(chartName, chartVersion);
        File file = new File(tgzFilePath);
        boolean download = false;
        if (!file.exists()) {
            file = downloadHelmChart(registry, chartName, chartVersion);
            download = true;
        }
        try {
            return getHelmChartFromFile(chartName, chartVersion, file);
        } finally {
            // 把下载的临时文件删除
            if (download) {
                file.delete();
            }
        }
    }

    @Override
    public HelmChartFile getHelmChartFromRegistry(String clusterId, String namespace, String name, String type) {
        MiddlewareClusterDTO cluster = clusterService.findById(clusterId);
        Middleware middleware = middlewareService.detail(clusterId, namespace, name, type);
        if (StringUtils.isEmpty(middleware.getChartVersion())) {
            BeanMiddlewareInfo beanMiddlewareInfo = middlewareInfoService.list(clusterId).stream()
                .filter(info -> info.getChartName().equals(type)).collect(Collectors.toList()).get(0);
            return getHelmChartFromRegistry(cluster.getRegistry(), type, beanMiddlewareInfo.getChartVersion());
        }
        return getHelmChartFromRegistry(cluster.getRegistry(), type, middleware.getChartVersion());
    }

    @Override
    public File downloadHelmChart(Registry registry, String chartName, String chartVersion) {
        if (!RegistryType.isSelfType(registry.getType())) {
            throw new BusinessException(ErrorMessage.REGISTRY_TYPE_NOT_SUPPORT_INTERFACE);
        }
        return helmChartWrapper.downloadHelmChart(registry, chartName, chartVersion);
    }

    @Override
    public HelmChartFile getHelmChartFromFile(String chartName, String chartVersion, File file) {
        File tarFileDir = null;
        String helmChartPath = null;
        // 如果没有指定chatName，则认为是手动上传的文件
        boolean isManual = StringUtils.isBlank(chartName);
        if (isManual) {
            helmChartPath = file.getParent();
        } else {
            helmChartPath = getHelmChartFilePath(chartName, chartVersion);
        }
        tarFileDir = new File(helmChartPath);

        if (null != tarFileDir && !tarFileDir.exists() && !tarFileDir.mkdirs()) {
            throw new BusinessException(ErrorMessage.CREATE_TEMPORARY_FILE_ERROR);
        }
        List<String> results = CmdExecUtil.runCmd(false, "cd", file.getParent(), "&&", "tar", "-zxvf",
            file.getAbsolutePath(), "-C", helmChartPath);
        if (CollectionUtils.isEmpty(results)) {
            log.error("解压chart文件错误");
            throw new BusinessException(ErrorMessage.HELM_CHART_UNZIP_ERROR);
        }
        log.info("chart 文件内容:{}", JSONObject.toJSONString(results));
        String[] resultsArr;
        if (results.get(0).startsWith("./") && results.size() > 1) {
            resultsArr = results.get(1).split(" ");
        } else {
            resultsArr = results.get(0).split(" ");
        }
        String tarFileName = resultsArr[resultsArr.length - 1].split(File.separator)[0];
        String tarFilePath = helmChartPath + File.separator + tarFileName;
        // 从下载的包获取参数文件，描述，资源yaml
        String valueYaml = HelmChartUtil.getValueYaml(tarFilePath);
        Map<String, Object> infoMap = HelmChartUtil.getInfoMap(tarFilePath);
        String description = infoMap.get("description") == null ? null : infoMap.get("description").toString();
        String iconPath = infoMap.get("icon") == null ? null : infoMap.get("icon").toString();
        String type = infoMap.get("type") == null ? null : infoMap.get("type").toString();
        String appVersion = infoMap.get("appVersion") == null ? null : infoMap.get("appVersion").toString();
        String official = infoMap.getOrDefault("owner", "").toString();
        List<Map<String, String>> dependencies = infoMap.containsKey("dependencies")
            ? (List<Map<String, String>>)infoMap.get("dependencies") : new ArrayList<>();
        if (isManual) {
            chartName = infoMap.get("name") == null ? null : infoMap.get("name").toString();
            chartVersion = infoMap.get("version") == null ? null : infoMap.get("version").toString();
        }
        Map<String, String> yamlFileMap = HelmChartUtil.getYamlFileMap(tarFilePath);
        yamlFileMap.putAll(HelmChartUtil.getParameters(tarFilePath));
        yamlFileMap.put(CHART_YAML_NAME, JSONObject.toJSONString(infoMap));

        // 封装返回结果
        return new HelmChartFile(chartName, chartVersion, description, valueYaml, yamlFileMap, "1", tarFileName,
            iconPath, type, appVersion, CollectionUtils.isEmpty(dependencies) ? new HashMap<>() : dependencies.get(0),
            official);
    }

    @Override
    public void coverYamlFile(HelmChartFile helmChart) {
        String unzipTarFilePath = getHelmChartFilePath(helmChart.getChartName(), helmChart.getChartVersion())
            + File.separator + helmChart.getTarFileName();
        try {
            // 覆盖写入values.yaml
            FileUtil.writeToLocal(unzipTarFilePath, VALUES_YAML_NAME,
                helmChart.getValueYaml().replace("\\n", "\n").replace("\\\"", "\""));
        } catch (IOException e) {
            log.error("写出values.yaml文件异常：chart包{}:{}", helmChart.getChartName(), helmChart.getChartVersion(), e);
            throw new BusinessException(ErrorMessage.HELM_CHART_WRITE_ERROR);
        }
    }

    @Override
    public void coverTemplateFile(HelmChartFile helmChart, String fileName) {
        String unzipTarFilePath = getHelmChartFilePath(helmChart.getChartName(), helmChart.getChartVersion())
            + File.separator + helmChart.getTarFileName();
        try {
            // 覆盖写入values.yaml
            FileUtil.writeToLocal(unzipTarFilePath, TEMPLATES + File.separator + fileName,
                helmChart.getYamlFileMap().get(fileName));
        } catch (IOException e) {
            log.error("写出{}文件异常：chart包{}:{}", fileName, helmChart.getChartName(), helmChart.getChartVersion(), e);
            throw new BusinessException(ErrorMessage.HELM_CHART_WRITE_ERROR);
        }
    }

    @Override
    public List<HelmListInfo> listHelm(String namespace, String name, MiddlewareClusterDTO cluster) {
        String cmd = "helm list --kube-apiserver " + cluster.getAddress() + " --kubeconfig "
            + clusterCertService.getKubeConfigFilePath(cluster.getId())
            + (StringUtils.isBlank(namespace) ? " -A" : " -n " + namespace);
        List<String> res = execCmd(cmd, null);
        if (CollectionUtils.isEmpty(res)) {
            return new ArrayList<>(0);
        }
        List<HelmListInfo> list = new ArrayList<>(res.size() - 1);
        boolean filterByName = StringUtils.isNotBlank(name);
        for (int i = 1; i < res.size(); i++) {
            // NAME NAMESPACE REVISION UPDATED STATUS CHART APP VERSION
            // mysql default 1 2021-03-31 15:53:57.044997 +0800 CST deployed mysql-0.1.0 5.7.21
            String[] infos = res.get(i).split("\\s+");
            if ((filterByName && !infos[0].equals(name)) || infos.length < 10) {
                continue;
            }
            HelmListInfo helm = new HelmListInfo();
            helm.setName(infos[0]);
            helm.setNamespace(infos[1]);
            helm.setRevision(infos[2]);
            helm.setUpdateTime(infos[3] + infos[4] + infos[5] + infos[6]);
            helm.setStatus(infos[7]);
            helm.setChart(infos[8]);
            helm.setAppVersion(infos[9]);
            list.add(helm);
        }
        return list;
    }

    @Override
    public JSONObject getInstalledValues(Middleware middleware, MiddlewareClusterDTO cluster) {
        return getInstalledValues(middleware.getName(), middleware.getNamespace(), cluster);
    }

    @Override
    public JSONObject getInstalledValues(String name, String namespace, MiddlewareClusterDTO cluster) {
        String cmd = String.format("helm get values %s -n %s -a --kube-apiserver %s --kubeconfig %s", name, namespace,
            cluster.getAddress(), clusterCertService.getKubeConfigFilePath(cluster.getId()));
        List<String> values = execCmd(cmd, notFoundMsg());
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        // 第0行是COMPUTED VALUES:，直接跳过
        for (int i = 1; i < values.size(); i++) {
            sb.append(values.get(i)).append("\n");
        }
        Yaml yaml = new Yaml();
        return yaml.loadAs(sb.toString(), JSONObject.class);
    }

    @Override
    public String packageChart(String unzipFileName, String chartName, String chartVersion) {
        String tarFileDir = getHelmChartFilePath(chartName, chartVersion);
        // 打包文件，返回信息如：Successfully packaged chart and saved it to: /xxx/xx/mysql-0.1.0.tgz，取/最后一段为包名
        String packageCmd =
            String.format("helm package %s -d %s", tarFileDir + File.separator + unzipFileName, tarFileDir);
        List<String> packageRes = execCmd(packageCmd, null);
        String tgzFileName = packageRes.get(0).substring(packageRes.get(0).lastIndexOf("/") + 1);
        return tarFileDir + File.separator + tgzFileName;
    }

    @Override
    public void install(Middleware middleware, String tgzFilePath, MiddlewareClusterDTO cluster) {
        install(middleware.getName(), middleware.getNamespace(), middleware.getChartName(),
            middleware.getChartVersion(), tgzFilePath, cluster);
    }

    @Override
    public void install(String name, String namespace, String chartName, String chartVersion, String tgzFilePath,
        MiddlewareClusterDTO cluster) {
        String tarFileDir = getHelmChartFilePath(chartName, chartVersion);
        String cmd = String.format("helm install %s %s --kube-apiserver %s --kubeconfig %s -n %s", name, tgzFilePath,
            cluster.getAddress(), clusterCertService.getKubeConfigFilePath(cluster.getId()), namespace);

        // 先dry-run发布下，避免包不正确
        execCmd(cmd + " --dry-run", null);
        // 正式发布
        execCmd(cmd, null);
        // 把当前目录删除
        FileUtil.deleteFile(tarFileDir);
    }

    @Override
    public QuestionYaml getQuestionYaml(HelmChartFile helmChartFile) {
        try {
            Yaml yaml = new Yaml();
            JSONObject question = yaml.loadAs(HelmChartUtil
                    .getQuestionYaml(getHelmChartFilePath(helmChartFile.getChartName(), helmChartFile.getChartVersion())
                        + File.separator + helmChartFile.getTarFileName()),
                JSONObject.class);
            return JSONObject.parseObject(JSONObject.toJSONString(question), QuestionYaml.class);
        } catch (Exception e) {
            log.error("中间件{} 获取question.yml失败", helmChartFile.getChartName() + ":" + helmChartFile.getChartVersion());
            throw new CaasRuntimeException(ErrorMessage.CREATE_DYNAMIC_FORM_FAILED);
        }
    }

    private List<String> execCmd(String cmd, Function<String, String> dealWithErrMsg) {
        List<String> res = new ArrayList<>();
        CmdExecUtil.execCmd(cmd, inputMsg -> {
            res.add(inputMsg);
            return inputMsg;
        }, dealWithErrMsg == null ? warningMsg() : dealWithErrMsg);
        return res;
    }

    private Function<String, String> warningMsg() {
        return errorMsg -> {
            if (errorMsg.startsWith("WARNING: ") || errorMsg.contains("warning: ")) {
                return errorMsg;
            }
            throw new RuntimeException(errorMsg);
        };
    }

    private Function<String, String> notFoundMsg() {
        return errorMsg -> {
            if (errorMsg.startsWith("WARNING: ") || errorMsg.contains("warning: ") || errorMsg.endsWith(
                "release: not found")) {
                return errorMsg;
            }
            throw new RuntimeException(errorMsg);
        };
    }

    @Override
    public void upgrade(Middleware middleware, String updateValues, MiddlewareClusterDTO cluster) {
        // helm upgrade
        if (StringUtils.isBlank(updateValues)) {
            return;
        }
        String chartName = middleware.getChartName();
        String chartVersion = middleware.getChartVersion();

        // 先获取chart文件
        HelmChartFile helmChart = getHelmChartFromRegistry(cluster.getRegistry(), chartName, chartVersion);

        JSONObject values = getInstalledValues(middleware, cluster);
        Yaml yaml = new Yaml();
        String valuesYaml = yaml.dumpAsMap(values);
        String tempValuesYamlDir = getTempValuesYamlDir();
        String tempValuesYamlName = chartName + "-" + chartVersion + "-" + System.currentTimeMillis() + ".yaml";
        try {
            FileUtil.writeToLocal(tempValuesYamlDir, tempValuesYamlName, valuesYaml);
        } catch (IOException e) {
            log.error("写出values.yaml文件异常：chart包{}:{}", helmChart.getChartName(), helmChart.getChartVersion(), e);
            throw new BusinessException(ErrorMessage.HELM_CHART_WRITE_ERROR);
        }

        String tgzFilePath = getTgzFilePath(chartName, chartVersion);
        File tgzFile = new File(tgzFilePath);
        if (!tgzFile.exists()) {
            // helm package
            tgzFilePath = packageChart(helmChart.getTarFileName(), chartName, chartVersion);
        }
        String tempValuesYamlPath = tempValuesYamlDir + File.separator + tempValuesYamlName;
        String cmd = String.format("helm upgrade %s %s --values %s --set %s -n %s --kube-apiserver %s --kubeconfig %s ",
            middleware.getName(), tgzFilePath, tempValuesYamlPath, updateValues, middleware.getNamespace(),
            cluster.getAddress(), clusterCertService.getKubeConfigFilePath(cluster.getId()));
        try {
            execCmd(cmd, null);
        } finally {
            // 删除文件
            FileUtil.deleteFile(tempValuesYamlPath, getHelmChartFilePath(chartName, chartVersion));
        }
    }

    @Override
    public void upgradeInstall(String name, String namespace, String setValues, String chartName, String chartVersion,
        MiddlewareClusterDTO cluster) {
        // 先下到本地
        File file = downloadHelmChart(cluster.getRegistry(), chartName, chartVersion);
        try {
            upgradeInstall(name, namespace, setValues, file.getAbsolutePath(), cluster);
        } finally {
            // 把下载的临时文件删除
            file.delete();
        }
    }

    @Override
    public void upgradeInstall(String name, String namespace, String setValues, String chartUrl,
        MiddlewareClusterDTO cluster) {
        String cmd = String.format("helm upgrade --install %s %s --set %s -n %s --kube-apiserver %s --kubeconfig %s ",
            name, chartUrl, setValues, namespace, cluster.getAddress(),
            clusterCertService.getKubeConfigFilePath(cluster.getId()));

        execCmd(cmd, null);
    }

    @Override
    public void uninstall(Middleware middleware, MiddlewareClusterDTO cluster) {
        // query first, if not exists then we need not do anything
        List<HelmListInfo> helms = listHelm(middleware.getNamespace(), middleware.getName(), cluster);
        if (CollectionUtils.isEmpty(helms)) {
            return;
        }

        // delete helm
        String cmd = String.format("helm uninstall %s -n %s --kube-apiserver %s --kubeconfig %s", middleware.getName(),
            middleware.getNamespace(), cluster.getAddress(), clusterCertService.getKubeConfigFilePath(cluster.getId()));
        execCmd(cmd, null);
    }

    @Override
    public void editOperatorChart(String clusterId, String operatorChartPath, String name) {
        Yaml yaml = new Yaml();
        MiddlewareClusterDTO cluster = clusterService.findById(clusterId);
        Registry registry = cluster.getRegistry();

        JSONObject values = yaml.loadAs(HelmChartUtil.getValueYaml(operatorChartPath), JSONObject.class);
        values.getJSONObject("image").put("repository", registry.getRegistryAddress() + "/"
            + (StringUtils.isBlank(registry.getImageRepo()) ? registry.getChartRepo() : registry.getImageRepo()) + "/"
            + name);
        try {
            // 覆盖写入values.yaml
            FileUtil.writeToLocal(operatorChartPath, VALUES_YAML_NAME,
                yaml.dumpAsMap(values).replace("\\n", "\n").replace("\\\"", "\""));
        } catch (IOException e) {
            throw new BusinessException(ErrorMessage.HELM_CHART_WRITE_ERROR);
        }
    }

    private String getUploadPath() {
        return uploadPath + SUB_DIR;
    }

    private String getHelmChartFilePath(String chartName, String chartVersion) {
        return getUploadPath() + chartName + File.separator + chartVersion;
    }

    private String getTempValuesYamlDir() {
        return getUploadPath() + "values";
    }

    private String getTgzFilePath(String chartName, String chartVersion) {
        return getHelmChartFilePath(chartName, chartVersion) + File.separator + chartName + "-" + chartVersion + ".tgz";
    }

}
