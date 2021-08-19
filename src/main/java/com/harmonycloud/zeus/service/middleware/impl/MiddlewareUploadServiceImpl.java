package com.harmonycloud.zeus.service.middleware.impl;import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import com.harmonycloud.caas.common.base.BaseResult;import com.harmonycloud.caas.common.enums.ErrorMessage;import com.harmonycloud.caas.common.exception.BusinessException;import com.harmonycloud.caas.common.model.middleware.MiddlewareClusterDTO;import com.harmonycloud.caas.common.model.middleware.Namespace;import com.harmonycloud.caas.common.model.registry.HelmChartFile;import com.harmonycloud.zeus.bean.BeanMiddlewareInfo;import com.harmonycloud.zeus.dao.BeanMiddlewareInfoMapper;import com.harmonycloud.zeus.integration.registry.HelmChartWrapper;import com.harmonycloud.zeus.integration.registry.bean.harbor.HelmListInfo;import com.harmonycloud.zeus.service.AbstractBaseService;import com.harmonycloud.zeus.service.k8s.ClusterService;import com.harmonycloud.zeus.service.k8s.NamespaceService;import com.harmonycloud.zeus.service.middleware.MiddlewareAlertsService;import com.harmonycloud.zeus.service.middleware.MiddlewareCustomConfigService;import com.harmonycloud.zeus.service.middleware.MiddlewareUploadService;import com.harmonycloud.zeus.service.registry.HelmChartService;import com.harmonycloud.tool.file.FileUtil;import io.fabric8.kubernetes.api.model.ObjectMeta;import lombok.extern.slf4j.Slf4j;import org.apache.commons.io.FileUtils;import org.apache.commons.lang3.BooleanUtils;import org.apache.commons.lang3.StringUtils;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.util.CollectionUtils;import org.springframework.web.multipart.MultipartFile;import javax.annotation.Resource;import java.io.*;import java.nio.MappedByteBuffer;import java.nio.channels.FileChannel;import java.nio.file.Files;import java.nio.file.Path;import java.nio.file.Paths;import java.time.LocalDateTime;import java.util.Collections;import java.util.HashMap;import java.util.List;import java.util.Map;import java.util.stream.Collectors;import static com.harmonycloud.caas.common.constants.CommonConstant.DOT;import static com.harmonycloud.caas.common.constants.CommonConstant.LINE;import static com.harmonycloud.caas.common.constants.middleware.MiddlewareConstant.HARMONY_CLOUD;import static com.harmonycloud.caas.common.constants.registry.HelmChartConstant.ICON_SVG;import static com.harmonycloud.caas.common.constants.registry.HelmChartConstant.SVG;/** * @Author: zack chen * @Date: 2021/5/14 11:00 上午 */@Slf4j@Service@Transactional(rollbackFor = {RuntimeException.class})public class MiddlewareUploadServiceImpl extends AbstractBaseService implements MiddlewareUploadService {    @Value("${system.upload.path:/usr/local/zeus-pv/upload}")    private String uploadPath;    @Value("${system.images.path:/usr/local/zeus-pv/images/middleware}")    private String imagePath;    @Value("${k8s.component.helm:/usr/local/zeus-pv/helm}")    private String helmPath;    @Value("${k8s.component.middleware:/usr/local/zeus-pv/middleware}")    private String middlewarePath;    @Autowired    private HelmChartService helmChartService;    @Autowired    private MiddlewareAlertsService middlewareAlertsService;    @Autowired    private MiddlewareCustomConfigService middlewareCustomConfigService;    @Autowired    private BeanMiddlewareInfoMapper middlewareInfoMapper;    /**     * 中间件上架     * @param clusterId     * @param fileIn     */    @Override    public void upload(String clusterId, MultipartFile fileIn) {        File tempStoredFile = null;        File tarFileDir = null;        try {            long tempId = System.currentTimeMillis();            // 临时保存到本地            String fileName = fileIn.getOriginalFilename();            String tempDirPath = uploadPath + File.separator + "temp" + File.separator + tempId + File.separator;            tarFileDir = new File(tempDirPath);            if (!tarFileDir.exists() && !tarFileDir.mkdirs()) {                throw new BusinessException(ErrorMessage.CREATE_TEMPORARY_FILE_ERROR);            }            tempStoredFile = new File(tempDirPath + fileName);            fileIn.transferTo(tempStoredFile);            // 解析包并入库信息.根据包名称版本进行更新            HelmChartFile helmChartFile = helmChartService.getHelmChartFromFile("", "", tempStoredFile);            // 更新告警规则和自定义配置至数据库            update2Mysql(helmChartFile);            // 将helm chart信息 存入数据库            save2Mysql(helmChartFile, tempDirPath, clusterId);            // 保存helm chart至挂载目录            File mountDir = new File(helmPath);            if (mountDir.exists() || mountDir.mkdirs()) {                File mountFile = new File(helmPath + File.separator + fileName);                FileUtils.copyFile(tempStoredFile, mountFile);            }            // 保存helm chart至本地目录            File tarFile = new File(middlewarePath + File.separator + fileName);            FileUtils.copyFile(tempStoredFile, tarFile);            // 创建operator            if (!CollectionUtils.isEmpty(helmChartFile.getDependency())) {                helmChartService.createOperator(tempDirPath, clusterId, helmChartFile);            }        } catch (IOException e) {            log.error("error when create temp file!", e);        } finally {            FileUtil.deleteFile(tarFileDir);        }    }    /**     * 更新数据至数据库     */    public void update2Mysql(HelmChartFile helmChart) {        try {            middlewareAlertsService.updateAlerts2Mysql(helmChart);        } catch (Exception e) {            log.error("更新告警规则至数据库失败");        }        try {            middlewareCustomConfigService.updateConfig2MySQL(helmChart);        } catch (Exception e) {            log.error("更新自定义配置至数据库失败");        }    }        public void save2Mysql(HelmChartFile helmChartFile, String tempDirPath, String clusterId) {        LambdaQueryWrapper<BeanMiddlewareInfo> queryWrapper = new LambdaQueryWrapper<>();        queryWrapper.eq(BeanMiddlewareInfo::getChartName, helmChartFile.getChartName())            .eq(BeanMiddlewareInfo::getChartVersion, helmChartFile.getChartVersion())            .eq(BeanMiddlewareInfo::getClusterId, clusterId).orderByDesc(BeanMiddlewareInfo::getUpdateTime);        List<BeanMiddlewareInfo> beanMiddlewareInfos = middlewareInfoMapper.selectList(queryWrapper);        BeanMiddlewareInfo middlewareInfo = new BeanMiddlewareInfo();        middlewareInfo.setName(helmChartFile.getChartName());        middlewareInfo.setChartName(helmChartFile.getChartName());        middlewareInfo.setType(helmChartFile.getType());        middlewareInfo.setName(helmChartFile.getChartName());        middlewareInfo.setDescription(helmChartFile.getDescription());        middlewareInfo.setClusterId(clusterId);        middlewareInfo.setOfficial(HARMONY_CLOUD.equals(helmChartFile.getOfficial()));        LocalDateTime now = LocalDateTime.now();        middlewareInfo.setUpdateTime(now);        List<Path> iconFiles = searchFiles(tempDirPath + helmChartFile.getTarFileName(), ICON_SVG);        if (!CollectionUtils.isEmpty(iconFiles)) {            // 获取image路径            String imagePath = helmChartFile.getChartName() + LINE + helmChartFile.getChartVersion() + DOT + SVG;            middlewareInfo.setImagePath(imagePath);            middlewareInfo.setImage(file2byte(iconFiles.get(0)));            try {                // 保存图片                saveImg(iconFiles.get(0).toString(), imagePath);            } catch (Exception e) {                log.error("中间件{} 保存图片失败", middlewareInfo.getChartName());            }        }        if (CollectionUtils.isEmpty(beanMiddlewareInfos)) {            middlewareInfo.setChartVersion(helmChartFile.getChartVersion());            middlewareInfo.setVersion(helmChartFile.getAppVersion());            middlewareInfo.setCreateTime(now);            middlewareInfoMapper.insert(middlewareInfo);        } else {            middlewareInfo.setId(beanMiddlewareInfos.get(0).getId());            middlewareInfoMapper.updateById(middlewareInfo);        }    }    /**     * 将文件转换成byte数组     * @param path     * @return     */    private byte[] file2byte(Path path){        try (FileChannel fc = new RandomAccessFile(path.toAbsolutePath().toString(), "r").getChannel()){            MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY, 0,                    fc.size()).load();            byte[] result = new byte[(int) fc.size()];            if (byteBuffer.remaining() > 0) {                byteBuffer.get(result, 0, byteBuffer.remaining());            }            return result;        } catch (IOException e) {            log.error("transform to byte failed", e);        }        return null;    }    /**     * 采用搜索的方式查找文件。在chart当前目录搜索。     * @param folderPath     * @param fileName     * @return     */    public static List<Path> searchFiles(String folderPath, final String fileName) {        try {            return Files.list(Paths.get(folderPath))                    .filter(s -> isIconMatch(s, fileName)).collect(Collectors.toList());        } catch (IOException e) {            log.error("search file failed", e);        }        return Collections.EMPTY_LIST;    }    /**     * chart图标名称匹配。如果没有指定文件名，则判断文件图片格式。     * @param path     * @param fileName     * @return     */    private static boolean isIconMatch(Path path, final String fileName){        return null == fileName                ? path.getFileName().toString().matches(".+(?i)(.svg|.png)$")                : StringUtils.equals(path.getFileName().toString(), fileName);    }    public void saveImg(String path, String name) throws IOException {        File img = new File(path);        File tarFile = new File(imagePath + File.separator + name);        FileUtils.copyFile(img, tarFile);    }}