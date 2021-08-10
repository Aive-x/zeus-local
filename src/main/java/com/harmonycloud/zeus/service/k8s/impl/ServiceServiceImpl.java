package com.harmonycloud.zeus.service.k8s.impl;

import com.harmonycloud.caas.common.model.middleware.PortDetailDTO;
import com.harmonycloud.caas.common.model.middleware.ServicePortDTO;
import com.harmonycloud.zeus.integration.cluster.ServiceWrapper;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareCRD;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareInfo;
import com.harmonycloud.zeus.integration.cluster.bean.MiddlewareStatus;
import com.harmonycloud.zeus.service.k8s.MiddlewareCRDService;
import com.harmonycloud.zeus.service.k8s.ServiceService;
import io.fabric8.kubernetes.api.model.ServicePort;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author tangtx
 * @date 4/02/21 4:56 PM
 */
@Service
public class ServiceServiceImpl implements ServiceService {

    @Autowired
    private MiddlewareCRDService middlewareCRDService;

    @Autowired
    private ServiceWrapper serviceWrapper;

    @Override
    public List<ServicePortDTO> list(String clusterId, String namespace, String name, String type) {
        MiddlewareCRD middleware = middlewareCRDService.getCR(clusterId, namespace, type, name);
        if (middleware == null || middleware.getStatus() == null) {
            return null;
        }
        MiddlewareStatus status = middleware.getStatus();
        Map<String, List<MiddlewareInfo>> stringListMap = status.getInclude();
        if (stringListMap == null || CollectionUtils.isEmpty(stringListMap.get("services"))) {
            return null;
        }
        List<MiddlewareInfo> middlewareInfoList = stringListMap.get("services");
        List<ServicePortDTO> servicePortDTOList = new ArrayList<>(10);
        for (MiddlewareInfo middlewareInfo : middlewareInfoList) {
            if (StringUtils.isBlank(middlewareInfo.getName())) {
                continue;
            }
            io.fabric8.kubernetes.api.model.Service service = serviceWrapper.get(clusterId, namespace, middlewareInfo.getName());
            if (service == null || service.getSpec() == null) {
                continue;
            }
            List<ServicePort> servicePortList = service.getSpec().getPorts();
            if (CollectionUtils.isEmpty(servicePortList)) {
                continue;
            }
            ServicePortDTO servicePortDTO = new ServicePortDTO();
            servicePortDTO.setServiceName(middlewareInfo.getName());
            List<PortDetailDTO> portDetailDTOList = new ArrayList<>();
            for (ServicePort servicePort : servicePortList) {
                if (servicePort.getPort() == null) {
                    continue;
                }
                PortDetailDTO portDetailDTO = new PortDetailDTO();
                portDetailDTO.setPort(servicePort.getPort() + "");
                portDetailDTO.setProtocol(servicePort.getProtocol());
                portDetailDTO.setTargetPort(servicePort.getTargetPort().getIntVal() + "");
                portDetailDTOList.add(portDetailDTO);
            }
            if (!CollectionUtils.isEmpty(portDetailDTOList)) {
                servicePortDTO.setPortDetailDtoList(portDetailDTOList);
            }
            servicePortDTOList.add(servicePortDTO);
        }
        return servicePortDTOList;
    }
}
