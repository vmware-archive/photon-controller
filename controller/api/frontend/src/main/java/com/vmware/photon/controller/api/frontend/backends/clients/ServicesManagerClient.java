/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.api.frontend.backends.clients;

import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ServiceNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceConfigurationState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceConfigurationStateFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.servicesmanager.servicedocuments.HarborServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.KubernetesServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.MesosServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceResizeTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.servicedocuments.SwarmServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceMaintenanceTask;
import com.vmware.photon.controller.servicesmanager.tasks.ServiceMaintenanceTaskFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service Manager Client Facade that exposes service manager functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class ServicesManagerClient {
  private static final Logger logger = LoggerFactory.getLogger(ServicesManagerClient.class);

  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP1     = "zookeeper_ip1";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP2     = "zookeeper_ip2";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP3     = "zookeeper_ip3";
  public static final String EXTENDED_PROPERTY_ETCD_IP1          = "etcd_ip1";
  public static final String EXTENDED_PROPERTY_ETCD_IP2          = "etcd_ip2";
  public static final String EXTENDED_PROPERTY_ETCD_IP3          = "etcd_ip3";

  private PhotonControllerXenonRestClient xenonClient;
  private ApiFeXenonRestClient apiFeXenonClient;

  @Inject
  public ServicesManagerClient(PhotonControllerXenonRestClient xenonClient, ApiFeXenonRestClient apiFeXenonClient)
      throws URISyntaxException {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
    this.apiFeXenonClient = apiFeXenonClient;
    this.apiFeXenonClient.start();
  }

  public KubernetesServiceCreateTaskState createKubernetesService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException {
    ServiceConfigurationState.State serviceConfiguration = getServiceConfiguration(ServiceType.KUBERNETES);
    String serviceId = createKubernetesServiceEntity(projectId, spec, serviceConfiguration);
    KubernetesServiceCreateTaskState createTask = new KubernetesServiceCreateTaskState();
    createTask.serviceId = serviceId;
    createTask.workerBatchExpansionSize =
        spec.getWorkerBatchExpansionSize() == 0 ? null : spec.getWorkerBatchExpansionSize();

    // Post createTask to KubernetesServiceCreateTask
    Operation operation = xenonClient.post(
        ServiceUriPaths.KUBERNETES_SERVICE_CREATE_TASK, createTask);
    return operation.getBody(KubernetesServiceCreateTaskState.class);
  }

  public KubernetesServiceCreateTaskState getKubernetesServiceCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(KubernetesServiceCreateTaskState.class);
  }

  public MesosServiceCreateTaskState createMesosService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException {
    ServiceConfigurationState.State serviceConfiguration = getServiceConfiguration(ServiceType.MESOS);
    String serviceId = createMesosServiceEntity(projectId, spec, serviceConfiguration);
    MesosServiceCreateTaskState createTask = new MesosServiceCreateTaskState();
    createTask.serviceId = serviceId;
    createTask.workerBatchExpansionSize =
        spec.getWorkerBatchExpansionSize() == 0 ? null : spec.getWorkerBatchExpansionSize();

    // Post createSpec to MesosServiceCreateTask
    Operation operation = xenonClient.post(
        ServiceUriPaths.MESOS_SERVICE_CREATE_TASK, createTask);
    return operation.getBody(MesosServiceCreateTaskState.class);
  }

  public MesosServiceCreateTaskState getMesosServiceCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(MesosServiceCreateTaskState.class);
  }

  public SwarmServiceCreateTaskState createSwarmService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException {
    ServiceConfigurationState.State serviceConfiguration = getServiceConfiguration(ServiceType.SWARM);
    String serviceId = createSwarmServiceEntity(projectId, spec, serviceConfiguration);
    SwarmServiceCreateTaskState createTask = new SwarmServiceCreateTaskState();
    createTask.serviceId = serviceId;
    createTask.workerBatchExpansionSize =
        spec.getWorkerBatchExpansionSize() == 0 ? null : spec.getWorkerBatchExpansionSize();

    // Post createSpec to SwarmServiceCreateTask
    Operation operation = xenonClient.post(
        ServiceUriPaths.SWARM_SERVICE_CREATE_TASK, createTask);
    return operation.getBody(SwarmServiceCreateTaskState.class);
  }

  public SwarmServiceCreateTaskState getSwarmServiceCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(SwarmServiceCreateTaskState.class);
  }

  public HarborServiceCreateTaskState createHarborService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException {
    ServiceConfigurationState.State serviceConfiguration = getServiceConfiguration(ServiceType.HARBOR);
    String serviceId = createHarborServiceEntity(projectId, spec, serviceConfiguration);
    HarborServiceCreateTaskState createTask = new HarborServiceCreateTaskState();
    createTask.serviceId = serviceId;

    // Post createTask to HarborServiceCreateTaskService
    Operation operation = xenonClient.post(
        ServiceUriPaths.HARBOR_SERVICE_CREATE_TASK, createTask);
    return operation.getBody(HarborServiceCreateTaskState.class);
  }

  public HarborServiceCreateTaskState getHarborServiceCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(HarborServiceCreateTaskState.class);
  }

  public ServiceResizeTaskState resizeService(String serviceId, ServiceResizeOperation resizeOperation) {
    // Translate API ServiceResizeOperation to service manager ServiceResizeTaskState
    ServiceResizeTaskState resizeTask = new ServiceResizeTaskState();
    resizeTask.serviceId = serviceId;
    resizeTask.newWorkerCount = resizeOperation.getNewWorkerCount();
    Operation operation = xenonClient.post(
        ServiceUriPaths.SERVICE_RESIZE_TASK, resizeTask);
    return operation.getBody(ServiceResizeTaskState.class);
  }

  public ServiceResizeTaskState getServiceResizeStatus(String resizeTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(resizeTaskLink);
    return operation.getBody(ServiceResizeTaskState.class);
  }

  public Service getService(String serviceId) throws ServiceNotFoundException {
    String uri = ServiceStateFactory.SELF_LINK + "/" + serviceId;
    com.vmware.xenon.common.Operation operation;
    try {
      operation = apiFeXenonClient.get(uri);
    } catch (DocumentNotFoundException ex) {
      throw new ServiceNotFoundException(serviceId);
    }

    ServiceState.State serviceDocument = operation.getBody(ServiceState.State.class);
    return toApiRepresentation(serviceDocument);
  }

  public ServiceDeleteTaskState deleteService(String serviceId) {
    ServiceDeleteTaskState deleteTask = new ServiceDeleteTaskState();
    deleteTask.serviceId = serviceId;

    Operation operation = xenonClient.post(
        ServiceUriPaths.SERVICE_DELETE_TASK, deleteTask);

    return operation.getBody(ServiceDeleteTaskState.class);
  }

  public void triggerMaintenance(String serviceId) {
    ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    String uri = ServiceMaintenanceTaskFactory.SELF_LINK + "/" + serviceId;
    Operation operation = xenonClient.patch(uri, patchState);
  }

  public ServiceDeleteTaskState getServiceDeletionStatus(String deletionTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(deletionTaskLink);
    return operation.getBody(ServiceDeleteTaskState.class);
  }

  public ResourceList<Service> getServices(String projectId, Optional<Integer> pageSize) throws ExternalException {
    ServiceDocumentQueryResult queryResult = apiFeXenonClient.queryDocuments(
        ServiceState.State.class,
        ImmutableMap.of("projectId", projectId),
        pageSize,
        true);

    return PaginationUtils.xenonQueryResultToResourceList(ServiceState.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  public ResourceList<Service> getServicesPages(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = apiFeXenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(ServiceState.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  public int getNumber(Optional<String> projectId) {
    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ServiceState.State.class));
    querySpec.query.addBooleanClause(kindClause);
    querySpec.options.add(QueryTask.QuerySpecification.QueryOption.COUNT);

    if (projectId.isPresent()) {
      QueryTask.Query clause = new QueryTask.Query()
          .setTermPropertyName("projectId")
          .setTermMatchValue(projectId.get());
      querySpec.query.addBooleanClause(clause);
    }

    com.vmware.xenon.common.Operation result = apiFeXenonClient.query(querySpec, true);
    ServiceDocumentQueryResult queryResult = result.getBody(QueryTask.class).results;
    return queryResult.documentCount.intValue();
  }

  private ServiceConfigurationState.State getServiceConfiguration(ServiceType serviceType)
      throws SpecInvalidException {
    List<ServiceConfigurationState.State> configurations = apiFeXenonClient.queryDocuments(
        ServiceConfigurationState.State.class,
        ImmutableMap.of("documentSelfLink",
            ServiceConfigurationStateFactory.SELF_LINK + "/" + serviceType.toString().toLowerCase()));

    if (configurations.isEmpty()) {
      throw new SpecInvalidException("No service configuration exists for " + serviceType.toString() + " service");
    }

    return configurations.iterator().next();
  }

  private ServiceState.State assembleCommonServiceEntity(ServiceType serviceType,
                                                         String projectId,
                                                         ServiceCreateSpec spec,
                                                         ServiceConfigurationState.State serviceConfiguration)
    throws SpecInvalidException{

    String dns = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS);
    String gateway = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY);
    String netmask = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK);
    String sshKey = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY);

    // Verify the service entity
    if (dns == null) {
      throw new SpecInvalidException("Missing extended property: dns");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(dns)) {
      throw new SpecInvalidException("Invalid extended property: dns: " + dns);
    }

    if (gateway == null) {
      throw new SpecInvalidException("Missing extended property: gateway");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(gateway)) {
      throw new SpecInvalidException("Invalid extended property: gateway: " + gateway);
    }

    if (netmask == null) {
      throw new SpecInvalidException("Missing extended property: netmask");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(netmask)) {
      throw new SpecInvalidException("Invalid extended property: netmask: " + netmask);
    }

    ServiceState.State service = new ServiceState.State();
    service.serviceState = com.vmware.photon.controller.api.model.ServiceState.CREATING;
    service.serviceName = spec.getName();
    service.serviceType = serviceType;
    service.imageId = serviceConfiguration.imageId;
    service.projectId = projectId;
    service.diskFlavorName =
        spec.getDiskFlavor() == null || spec.getDiskFlavor().isEmpty() ?
            ServicesManagerConstants.VM_DISK_FLAVOR : spec.getDiskFlavor();
    service.masterVmFlavorName =
        spec.getVmFlavor() == null || spec.getVmFlavor().isEmpty() ?
            ServicesManagerConstants.MASTER_VM_FLAVOR : spec.getVmFlavor();
    service.otherVmFlavorName =
        spec.getVmFlavor() == null || spec.getVmFlavor().isEmpty() ?
            ServicesManagerConstants.OTHER_VM_FLAVOR : spec.getVmFlavor();
    service.vmNetworkId = spec.getVmNetworkId();
    service.workerCount = spec.getWorkerCount();
    service.extendedProperties = new HashMap<>();
    service.documentSelfLink = UUID.randomUUID().toString();
    service.extendedProperties = new HashMap<>();
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, dns);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, gateway);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, netmask);
    if (sshKey != null) {
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY, sshKey);
    }

    return service;
  }

  private String createKubernetesServiceEntity(String projectId,
                                               ServiceCreateSpec spec,
                                               ServiceConfigurationState.State serviceConfiguration)
    throws SpecInvalidException {

    List<String> etcdIps = new ArrayList<>();
    for (String property : Arrays.asList(EXTENDED_PROPERTY_ETCD_IP1, EXTENDED_PROPERTY_ETCD_IP2,
        EXTENDED_PROPERTY_ETCD_IP3)) {
      String etcdIp = spec.getExtendedProperties().get(property);
      if (etcdIp != null) {
        etcdIps.add(etcdIp);
      }
    }
    String masterIp = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    String containerNetwork =
        spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);
    String caCert =
        spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE);

    if (etcdIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: etcd ips");
    }

    for (String etcdIp : etcdIps) {
      if (etcdIp != null && !InetAddressValidator.getInstance().isValidInet4Address(etcdIp)) {
        throw new SpecInvalidException("Invalid extended property: etcd ip: " + etcdIp);
      }
    }

    if (masterIp == null) {
      throw new SpecInvalidException("Missing extended property: master ip");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(masterIp)) {
      throw new SpecInvalidException("Invalid extended property: master ip: " + masterIp);
    }

    if (containerNetwork == null) {
      throw new SpecInvalidException("Missing extended property: "
          + ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);
    } else {
      String[] segments = containerNetwork.split("/");
      SpecInvalidException error = new SpecInvalidException("Invalid extended property: "
          + ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK + ": "
          + containerNetwork);

      // Check that container network is of CIDR format.
      if (segments.length != 2) {
        throw error;
      }

      // Check the first segment of the container network is a valid address.
      if (!InetAddressValidator.getInstance().isValidInet4Address(segments[0])) {
        throw error;
      }

      // Check the second segment of the container network is a valid netmask.
      try {
        int cidr = Integer.parseInt(segments[1]);
        if (cidr < 0 || cidr > 32) {
          throw error;
        }
      } catch (NumberFormatException e) {
        throw error;
      }
    }

    // Assemble the service entity
    ServiceState.State service = assembleCommonServiceEntity(
        ServiceType.KUBERNETES, projectId, spec, serviceConfiguration);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, containerNetwork);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS, serializeIpAddresses(etcdIps));
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, masterIp);

    if (caCert != null) {
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE, caCert);
    }

    // Create the service entity
    apiFeXenonClient.post(
        ServiceStateFactory.SELF_LINK,
        service);

    return service.documentSelfLink;
  }

  private String createMesosServiceEntity(String projectId,
                                          ServiceCreateSpec spec,
                                          ServiceConfigurationState.State serviceConfiguration)
    throws SpecInvalidException {

    List<String> zookeeperIps = new ArrayList<>();
    for (String property : Arrays.asList(EXTENDED_PROPERTY_ZOOKEEPER_IP1, EXTENDED_PROPERTY_ZOOKEEPER_IP2,
        EXTENDED_PROPERTY_ZOOKEEPER_IP3)) {
      String zookeeperIp = spec.getExtendedProperties().get(property);
      if (zookeeperIp != null) {
        zookeeperIps.add(zookeeperIp);
      }
    }

    if (zookeeperIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: zookeeper ips");
    }

    for (String zookeeperIp : zookeeperIps) {
      if (zookeeperIp != null && !InetAddressValidator.getInstance().isValidInet4Address(zookeeperIp)) {
        throw new SpecInvalidException("Invalid extended property: zookeeper ip: " + zookeeperIp);
      }
    }

    // Assemble the service entity
    ServiceState.State service = assembleCommonServiceEntity(
        ServiceType.MESOS, projectId, spec, serviceConfiguration);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS,
        serializeIpAddresses(zookeeperIps));

    // Create the service entity
    apiFeXenonClient.post(
        ServiceStateFactory.SELF_LINK,
        service);

    return service.documentSelfLink;
  }

  private String createSwarmServiceEntity(String projectId,
                                          ServiceCreateSpec spec,
                                          ServiceConfigurationState.State serviceConfiguration)
    throws SpecInvalidException {

    List<String> etcdIps = new ArrayList<>();
    for (String property : Arrays.asList(EXTENDED_PROPERTY_ETCD_IP1, EXTENDED_PROPERTY_ETCD_IP2,
        EXTENDED_PROPERTY_ETCD_IP3)) {
      String etcdIp = spec.getExtendedProperties().get(property);
      if (etcdIp != null) {
        etcdIps.add(etcdIp);
      }
    }

    if (etcdIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: etcd ips");
    }

    for (String etcdIp : etcdIps) {
      if (etcdIp != null && !InetAddressValidator.getInstance().isValidInet4Address(etcdIp)) {
        throw new SpecInvalidException("Invalid extended property: etcd ip: " + etcdIp);
      }
    }

    // Assemble the service entity
    ServiceState.State service = assembleCommonServiceEntity(
        ServiceType.SWARM, projectId, spec, serviceConfiguration);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS, serializeIpAddresses(etcdIps));

    // Create the service entity
    apiFeXenonClient.post(
        ServiceStateFactory.SELF_LINK,
        service);

    return service.documentSelfLink;
  }

  private String createHarborServiceEntity(String projectId,
                                           ServiceCreateSpec spec,
                                           ServiceConfigurationState.State serviceConfiguration)
      throws SpecInvalidException {

    String masterIp = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    String adminPassword = spec.getExtendedProperties().get(ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD);

    if (masterIp == null) {
      throw new SpecInvalidException("Missing extended property: " +
          ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(masterIp)) {
      throw new SpecInvalidException("Invalid extended property: master ip: " + masterIp);
    }

    if (adminPassword == null) {
      throw new SpecInvalidException("Missing extended property: " +
          ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD);
    }

    // Assemble the service entity
    ServiceState.State service = assembleCommonServiceEntity(
        ServiceType.HARBOR, projectId, spec, serviceConfiguration);
    // Even if a worker count was specified by the user, set it to 0.
    // This is because we currently don't support a multinode Harbor deployment.
    service.workerCount = 0;
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, masterIp);
    service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD, adminPassword);

    // Create the service entity
    apiFeXenonClient.post(
        ServiceStateFactory.SELF_LINK,
        service);

    return service.documentSelfLink;
  }

  private String serializeIpAddresses(List<String> ipAddresses) {
    StringBuilder sb = new StringBuilder();
    for (String ipAddress : ipAddresses) {
      if (ipAddress == null) {
        continue;
      }

      if (sb.length() != 0) {
        sb.append(",");
      }
      sb.append(ipAddress);
    }

    return sb.toString();
  }

  private Service toApiRepresentation(ServiceState.State serviceDocument) {
    Service service = new Service();

    String serviceId = ServiceUtils.getIDFromDocumentSelfLink(serviceDocument.documentSelfLink);
    service.setId(serviceId);
    service.setName(serviceDocument.serviceName);
    service.setType(serviceDocument.serviceType);
    service.setState(serviceDocument.serviceState);
    service.setProjectId(serviceDocument.projectId);
    service.setWorkerCount(serviceDocument.workerCount);
    service.setMasterVmFlavorName(serviceDocument.masterVmFlavorName);
    service.setOtherVmFlavorName(serviceDocument.otherVmFlavorName);
    service.setImageId(serviceDocument.imageId);
    if (serviceDocument.errorReason != null && !serviceDocument.errorReason.isEmpty()) {
      service.setErrorReason(serviceDocument.errorReason);
    }
    service.setExtendedProperties(serviceDocument.extendedProperties);

    return service;
  }
}
