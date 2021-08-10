package com.harmonycloud.zeus.service.middleware.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareCRD;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.harmonycloud.caas.common.enums.middleware.MiddlewareTypeEnum;
import com.harmonycloud.caas.common.model.middleware.Middleware;
import com.harmonycloud.caas.common.model.middleware.MiddlewareInfoDTO;
import com.harmonycloud.zeus.bean.BeanMiddlewareInfo;
import com.harmonycloud.zeus.dao.BeanMiddlewareInfoMapper;
import com.harmonycloud.zeus.service.k8s.MiddlewareCRDService;
import com.harmonycloud.zeus.service.middleware.MiddlewareInfoService;

import lombok.extern.slf4j.Slf4j;

/**
 * @author dengyulong
 * @date 2021/03/23
 */
@Slf4j
@Service
public class MiddlewareInfoServiceImpl implements MiddlewareInfoService {

    @Autowired
    private BeanMiddlewareInfoMapper middlewareInfoMapper;
    @Autowired
    private MiddlewareCRDService middlewareCRDService;

    @Override
    public List<BeanMiddlewareInfo> list(String clusterId) {
        QueryWrapper<BeanMiddlewareInfo> wrapper = new QueryWrapper<BeanMiddlewareInfo>().eq("status", true);
        List<BeanMiddlewareInfo> list = middlewareInfoMapper.selectList(wrapper);
        if (list == null) {
            list = new ArrayList<>(0);
        }
        if (StringUtils.isEmpty(clusterId)){
            return list;
        }
        list.forEach(l -> {
            if (StringUtils.isEmpty(l.getClusterId())) {
                l.setClusterId(clusterId);
                middlewareInfoMapper.updateById(l);
            }
        });
        return list.stream().filter(l -> l.getClusterId().equals(clusterId)).collect(Collectors.toList());
    }

    @Override
    public BeanMiddlewareInfo getMiddlewareInfo(String chartName, String chartVersion) {
        QueryWrapper<BeanMiddlewareInfo> wrapper = new QueryWrapper<BeanMiddlewareInfo>().eq("status", true)
            .eq("chart_name", chartName).eq("chart_version", chartVersion);
        List<BeanMiddlewareInfo> mwInfoList = middlewareInfoMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(mwInfoList)){
            return null;
        }
        return mwInfoList.get(0);
    }

    @Override
    public List<MiddlewareInfoDTO> list(String clusterId, String namespace) {
        List<BeanMiddlewareInfo> list = list(clusterId);
        if (list.size() == 0) {
            return new ArrayList<>(0);
        }

        List<MiddlewareCRD> crdList = middlewareCRDService.listCRD(clusterId, namespace, null);
        Map<String, List<Middleware>> middlewareMap = null;
        if (!CollectionUtils.isEmpty(crdList)) {
            middlewareMap = crdList.stream()
                .collect(Collectors.groupingBy(crd -> MiddlewareTypeEnum.findTypeByCrdType(crd.getSpec().getType()),
                    Collectors.mapping(c -> middlewareCRDService.simpleConvert(c), Collectors.toList())));
        }
        Map<String, List<Middleware>> finalMiddlewareMap = middlewareMap;
        return list.stream().map(info -> {
            MiddlewareInfoDTO dto = new MiddlewareInfoDTO();
            BeanUtils.copyProperties(info, dto);
            dto.setMiddlewares(finalMiddlewareMap == null || finalMiddlewareMap.get(dto.getChartName()) == null
                ? new ArrayList<>(0) : finalMiddlewareMap.get(dto.getChartName()))
                .setReplicas(dto.getMiddlewares().size()).setReplicasStatus(
                    dto.getMiddlewares().stream().allMatch(m -> StringUtils.equals(m.getStatus(), "Running")));
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Override
    public void update(BeanMiddlewareInfo middlewareInfo) {
        if (middlewareInfo.getId() != null) {
            middlewareInfoMapper.updateById(middlewareInfo);
        } else {
            QueryWrapper<BeanMiddlewareInfo> wrapper = new QueryWrapper<BeanMiddlewareInfo>()
                .eq("chart_name", middlewareInfo.getChartName()).eq("chart_version", middlewareInfo.getChartVersion());
            middlewareInfoMapper.update(middlewareInfo, wrapper);
        }
    }
    

}
