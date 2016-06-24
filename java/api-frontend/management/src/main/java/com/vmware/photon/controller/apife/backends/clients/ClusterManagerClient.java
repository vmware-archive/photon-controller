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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterResizeOperation;
import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

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
 * Cluster Manager Client Facade that exposes cluster manager functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class ClusterManagerClient {
  private static final Logger logger = LoggerFactory.getLogger(ClusterManagerClient.class);

  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP1     = "zookeeper_ip1";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP2     = "zookeeper_ip2";
  public static final String EXTENDED_PROPERTY_ZOOKEEPER_IP3     = "zookeeper_ip3";
  public static final String EXTENDED_PROPERTY_ETCD_IP1          = "etcd_ip1";
  public static final String EXTENDED_PROPERTY_ETCD_IP2          = "etcd_ip2";
  public static final String EXTENDED_PROPERTY_ETCD_IP3          = "etcd_ip3";

  private ClusterManagerXenonRestClient xenonClient;
  private ApiFeXenonRestClient apiFeXenonClient;

  @Inject
  public ClusterManagerClient(ClusterManagerXenonRestClient xenonClient, ApiFeXenonRestClient apiFeXenonClient)
      throws URISyntaxException {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
    this.apiFeXenonClient = apiFeXenonClient;
    this.apiFeXenonClient.start();
  }

  public KubernetesClusterCreateTask createKubernetesCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    ClusterConfigurationService.State clusterConfiguration = getClusterConfiguration(ClusterType.KUBERNETES);
    String clusterId = createKubernetesClusterEntity(projectId, spec, clusterConfiguration);
    KubernetesClusterCreateTask createTask = new KubernetesClusterCreateTask();
    createTask.clusterId = clusterId;
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    // Post createTask to KubernetesClusterCreateTaskService
    Operation operation = xenonClient.post(
        ServiceUriPaths.KUBERNETES_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(KubernetesClusterCreateTask.class);
  }

  public KubernetesClusterCreateTask getKubernetesClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(KubernetesClusterCreateTask.class);
  }

  public MesosClusterCreateTask createMesosCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    ClusterConfigurationService.State clusterConfiguration = getClusterConfiguration(ClusterType.MESOS);
    String clusterId = createMesosClusterEntity(projectId, spec, clusterConfiguration);
    MesosClusterCreateTask createTask = new MesosClusterCreateTask();
    createTask.clusterId = clusterId;
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    // Post createSpec to MesosClusterCreateTaskService
    Operation operation = xenonClient.post(
        ServiceUriPaths.MESOS_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(MesosClusterCreateTask.class);
  }

  public MesosClusterCreateTask getMesosClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(MesosClusterCreateTask.class);
  }

  public SwarmClusterCreateTask createSwarmCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    ClusterConfigurationService.State clusterConfiguration = getClusterConfiguration(ClusterType.SWARM);
    String clusterId = createSwarmClusterEntity(projectId, spec, clusterConfiguration);
    SwarmClusterCreateTask createTask = new SwarmClusterCreateTask();
    createTask.clusterId = clusterId;
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    // Post createSpec to SwarmClusterCreateTaskService
    Operation operation = xenonClient.post(
        ServiceUriPaths.SWARM_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(SwarmClusterCreateTask.class);
  }

  public SwarmClusterCreateTask getSwarmClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(SwarmClusterCreateTask.class);
  }

  public ClusterResizeTask resizeCluster(String clusterId, ClusterResizeOperation resizeOperation) {
    // Translate API ClusterResizeOperation to cluster manager ClusterResizeTask
    ClusterResizeTask resizeTask = new ClusterResizeTask();
    resizeTask.clusterId = clusterId;
    resizeTask.newSlaveCount = resizeOperation.getNewSlaveCount();
    Operation operation = xenonClient.post(
        ServiceUriPaths.CLUSTER_RESIZE_TASK_SERVICE, resizeTask);
    return operation.getBody(ClusterResizeTask.class);
  }

  public ClusterResizeTask getClusterResizeStatus(String resizeTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(resizeTaskLink);
    return operation.getBody(ClusterResizeTask.class);
  }

  public Cluster getCluster(String clusterId) throws ClusterNotFoundException {
    String uri = ClusterServiceFactory.SELF_LINK + "/" + clusterId;
    com.vmware.xenon.common.Operation operation;
    try {
      operation = apiFeXenonClient.get(uri);
    } catch (DocumentNotFoundException ex) {
      throw new ClusterNotFoundException(clusterId);
    }

    ClusterService.State clusterDocument = operation.getBody(ClusterService.State.class);
    return toApiRepresentation(clusterDocument);
  }

  public ClusterDeleteTask deleteCluster(String clusterId) {
    ClusterDeleteTask deleteTask = new ClusterDeleteTask();
    deleteTask.clusterId = clusterId;

    Operation operation = xenonClient.post(
        ServiceUriPaths.CLUSTER_DELETE_TASK_SERVICE, deleteTask);

    return operation.getBody(ClusterDeleteTask.class);
  }

  public ClusterDeleteTask getClusterDeletionStatus(String deletionTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(deletionTaskLink);
    return operation.getBody(ClusterDeleteTask.class);
  }

  public ResourceList<Cluster> getClusters(String projectId, Optional<Integer> pageSize) throws ExternalException {
    ServiceDocumentQueryResult queryResult = apiFeXenonClient.queryDocuments(
        ClusterService.State.class,
        ImmutableMap.of("projectId", projectId),
        pageSize,
        true);

    return PaginationUtils.xenonQueryResultToResourceList(ClusterService.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  public ResourceList<Cluster> getClustersPages(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = apiFeXenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(ClusterService.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  private ClusterConfigurationService.State getClusterConfiguration(ClusterType clusterType)
      throws SpecInvalidException {
    List<ClusterConfigurationService.State> configurations = apiFeXenonClient.queryDocuments(
        ClusterConfigurationService.State.class,
        ImmutableMap.of("documentSelfLink",
            ClusterConfigurationServiceFactory.SELF_LINK + "/" + clusterType.toString().toLowerCase()));

    if (configurations.isEmpty()) {
      throw new SpecInvalidException("No cluster configuration exists for " + clusterType.toString() + " cluster");
    }

    return configurations.iterator().next();
  }

  private ClusterService.State assembleCommonClusterEntity(ClusterType clusterType,
                                                           String projectId,
                                                           ClusterCreateSpec spec,
                                                           ClusterConfigurationService.State clusterConfiguration)
    throws SpecInvalidException{

    String dns = spec.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS);
    String gateway = spec.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY);
    String netmask = spec.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK);

    // Verify the cluster entity
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

    ClusterService.State cluster = new ClusterService.State();
    cluster.clusterState = ClusterState.CREATING;
    cluster.clusterName = spec.getName();
    cluster.clusterType = clusterType;
    cluster.imageId = clusterConfiguration.imageId;
    cluster.projectId = projectId;
    cluster.diskFlavorName =
        spec.getDiskFlavor() == null || spec.getDiskFlavor().isEmpty() ?
            ClusterManagerConstants.VM_DISK_FLAVOR : spec.getDiskFlavor();
    cluster.masterVmFlavorName =
        spec.getVmFlavor() == null || spec.getVmFlavor().isEmpty() ?
            ClusterManagerConstants.MASTER_VM_FLAVOR : spec.getVmFlavor();
    cluster.otherVmFlavorName =
        spec.getVmFlavor() == null || spec.getVmFlavor().isEmpty() ?
            ClusterManagerConstants.OTHER_VM_FLAVOR : spec.getVmFlavor();
    cluster.vmNetworkId = spec.getVmNetworkId();
    cluster.slaveCount = spec.getSlaveCount();
    cluster.extendedProperties = new HashMap<>();
    cluster.documentSelfLink = UUID.randomUUID().toString();
    cluster.extendedProperties = new HashMap<>();
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, dns);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, gateway);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, netmask);

    return cluster;
  }

  private String createKubernetesClusterEntity(String projectId,
                                               ClusterCreateSpec spec,
                                               ClusterConfigurationService.State clusterConfiguration)
    throws SpecInvalidException {

    List<String> etcdIps = new ArrayList<>();
    for (String property : Arrays.asList(EXTENDED_PROPERTY_ETCD_IP1, EXTENDED_PROPERTY_ETCD_IP2,
        EXTENDED_PROPERTY_ETCD_IP3)) {
      String etcdIp = spec.getExtendedProperties().get(property);
      if (etcdIp != null) {
        etcdIps.add(etcdIp);
      }
    }
    String masterIp = spec.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    String containerNetwork =
        spec.getExtendedProperties().get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);

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
          + ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);
    } else {
      String[] segments = containerNetwork.split("/");
      SpecInvalidException error = new SpecInvalidException("Invalid extended property: "
          + ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK + ": "
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

    // Assemble the cluster entity
    ClusterService.State cluster = assembleCommonClusterEntity(
        ClusterType.KUBERNETES, projectId, spec, clusterConfiguration);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, containerNetwork);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS, serializeIpAddresses(etcdIps));
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP, masterIp);

    // Create the cluster entity
    apiFeXenonClient.post(
        ClusterServiceFactory.SELF_LINK,
        cluster);

    return cluster.documentSelfLink;
  }

  private String createMesosClusterEntity(String projectId,
                                          ClusterCreateSpec spec,
                                          ClusterConfigurationService.State clusterConfiguration)
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

    // Assemble the cluster entity
    ClusterService.State cluster = assembleCommonClusterEntity(
        ClusterType.MESOS, projectId, spec, clusterConfiguration);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS,
        serializeIpAddresses(zookeeperIps));

    // Create the cluster entity
    apiFeXenonClient.post(
        ClusterServiceFactory.SELF_LINK,
        cluster);

    return cluster.documentSelfLink;
  }

  private String createSwarmClusterEntity(String projectId,
                                          ClusterCreateSpec spec,
                                          ClusterConfigurationService.State clusterConfiguration)
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

    // Assemble the cluster entity
    ClusterService.State cluster = assembleCommonClusterEntity(
        ClusterType.SWARM, projectId, spec, clusterConfiguration);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS, serializeIpAddresses(etcdIps));

    // Create the cluster entity
    apiFeXenonClient.post(
        ClusterServiceFactory.SELF_LINK,
        cluster);

    return cluster.documentSelfLink;
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

  private Cluster toApiRepresentation(ClusterService.State clusterDocument) {
    Cluster cluster = new Cluster();

    String clusterId = ServiceUtils.getIDFromDocumentSelfLink(clusterDocument.documentSelfLink);
    cluster.setId(clusterId);
    cluster.setName(clusterDocument.clusterName);
    cluster.setType(clusterDocument.clusterType);
    cluster.setState(clusterDocument.clusterState);
    cluster.setProjectId(clusterDocument.projectId);
    cluster.setSlaveCount(clusterDocument.slaveCount);
    cluster.setExtendedProperties(clusterDocument.extendedProperties);

    return cluster;
  }
}
