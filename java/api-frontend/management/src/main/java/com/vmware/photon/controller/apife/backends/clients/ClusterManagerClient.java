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

import com.vmware.dcp.common.Operation;
import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterResizeOperation;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cluster Manager Client Facade that exposes cluster manager functionality via high-level methods,
 * and hides DCP protocol details.
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

  private ClusterManagerDcpRestClient dcpClient;
  private ApiFeDcpRestClient apiFeDcpClient;

  @Inject
  public ClusterManagerClient(ClusterManagerDcpRestClient dcpClient, ApiFeDcpRestClient apiFeDcpClient)
      throws URISyntaxException {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
    this.apiFeDcpClient = apiFeDcpClient;
    this.apiFeDcpClient.start();
  }

  public KubernetesClusterCreateTask createKubernetesCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    // Translate API ClusterCreateSpec to cluster manager KubernetesClusterCreateTask
    KubernetesClusterCreateTask createTask = new KubernetesClusterCreateTask();
    createTask.clusterName = spec.getName();
    createTask.slaveCount = spec.getSlaveCount();
    createTask.diskFlavorName = spec.getDiskFlavor();
    createTask.projectId = projectId;
    createTask.masterVmFlavorName = spec.getVmFlavor();
    createTask.otherVmFlavorName = spec.getVmFlavor();
    createTask.vmNetworkId = spec.getVmNetworkId();
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    if (spec.getExtendedProperties() != null) {
      createTask.containerNetwork = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);
      createTask.dns = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS);
      createTask.gateway = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY);
      createTask.netmask = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK);
      createTask.etcdIps = new ArrayList<>();
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP1);
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP2);
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP3);
      createTask.masterIp = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    }
    if (createTask.dns == null) {
      throw new SpecInvalidException("Missing extended property: dns");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.dns)) {
      throw new SpecInvalidException("Invalid extended property: dns: " + createTask.dns);
    }

    if (createTask.gateway == null) {
      throw new SpecInvalidException("Missing extended property: gateway");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.gateway)) {
      throw new SpecInvalidException("Invalid extended property: gateway: " + createTask.gateway);
    }

    if (createTask.netmask == null) {
      throw new SpecInvalidException("Missing extended property: netmask");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.netmask)) {
      throw new SpecInvalidException("Invalid extended property: netmask: " + createTask.netmask);
    }

    if (createTask.etcdIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: etcd ips");
    }

    if (createTask.masterIp == null) {
      throw new SpecInvalidException("Missing extended property: master_ip");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.masterIp)) {
      throw new SpecInvalidException("Invalid extended property: master ip: " + createTask.masterIp);
    }

    if (createTask.containerNetwork == null) {
      throw new SpecInvalidException("Missing extended property: "
          + ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);
    } else {
      String[] segments = createTask.containerNetwork.split("/");
      SpecInvalidException error = new SpecInvalidException("Invalid extended property: "
          + ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK + ": "
          + createTask.containerNetwork);

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

    // Post createSpec to KubernetesClusterCreateTaskService
    Operation operation = dcpClient.postAndWait(
        ServiceUriPaths.KUBERNETES_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(KubernetesClusterCreateTask.class);
  }

  public KubernetesClusterCreateTask getKubernetesClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = dcpClient.getAndWait(creationTaskLink);
    return operation.getBody(KubernetesClusterCreateTask.class);
  }

  public MesosClusterCreateTask createMesosCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    // Translate API ClusterCreateSpec to cluster manager MesosClusterCreateTask
    MesosClusterCreateTask createTask = new MesosClusterCreateTask();
    createTask.clusterName = spec.getName();
    createTask.slaveCount = spec.getSlaveCount();
    createTask.diskFlavorName = spec.getDiskFlavor();
    createTask.projectId = projectId;
    createTask.masterVmFlavorName = spec.getVmFlavor();
    createTask.otherVmFlavorName = spec.getVmFlavor();
    createTask.vmNetworkId = spec.getVmNetworkId();
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    if (spec.getExtendedProperties() != null) {
      createTask.dns = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS);
      createTask.gateway = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY);
      createTask.netmask = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK);

      createTask.zookeeperIps = new ArrayList<>();
      addIpAddressToList(createTask.zookeeperIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ZOOKEEPER_IP1);
      addIpAddressToList(createTask.zookeeperIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ZOOKEEPER_IP2);
      addIpAddressToList(createTask.zookeeperIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ZOOKEEPER_IP3);
    }
    if (createTask.dns == null) {
      throw new SpecInvalidException("Missing extended property: dns");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.dns)) {
      throw new SpecInvalidException("Invalid extended property: dns: " + createTask.dns);
    }

    if (createTask.gateway == null) {
      throw new SpecInvalidException("Missing extended property: gateway");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.gateway)) {
      throw new SpecInvalidException("Invalid extended property: gateway: " + createTask.gateway);
    }

    if (createTask.netmask == null) {
      throw new SpecInvalidException("Missing extended property: netmask");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.netmask)) {
      throw new SpecInvalidException("Invalid extended property: netmask: " + createTask.netmask);
    }

    if (createTask.zookeeperIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: zookeeper ips");
    }

    // Post createSpec to MesosClusterCreateTaskService
    Operation operation = dcpClient.postAndWait(
        ServiceUriPaths.MESOS_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(MesosClusterCreateTask.class);
  }

  public MesosClusterCreateTask getMesosClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = dcpClient.getAndWait(creationTaskLink);
    return operation.getBody(MesosClusterCreateTask.class);
  }

  public SwarmClusterCreateTask createSwarmCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException {
    // Translate API ClusterCreateSpec to cluster manager SwarmClusterCreateTask
    SwarmClusterCreateTask createTask = new SwarmClusterCreateTask();
    createTask.clusterName = spec.getName();
    createTask.slaveCount = spec.getSlaveCount();
    createTask.diskFlavorName = spec.getDiskFlavor();
    createTask.projectId = projectId;
    createTask.masterVmFlavorName = spec.getVmFlavor();
    createTask.otherVmFlavorName = spec.getVmFlavor();
    createTask.vmNetworkId = spec.getVmNetworkId();
    createTask.slaveBatchExpansionSize =
        spec.getSlaveBatchExpansionSize() == 0 ? null : spec.getSlaveBatchExpansionSize();

    if (spec.getExtendedProperties() != null) {
      createTask.dns = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS);
      createTask.gateway = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY);
      createTask.netmask = spec.getExtendedProperties()
          .get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK);
      createTask.etcdIps = new ArrayList<>();
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP1);
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP2);
      addIpAddressToList(createTask.etcdIps, spec.getExtendedProperties(),
          EXTENDED_PROPERTY_ETCD_IP3);
    }
    if (createTask.dns == null) {
      throw new SpecInvalidException("Missing extended property: dns");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.dns)) {
      throw new SpecInvalidException("Invalid extended property: dns: " + createTask.dns);
    }

    if (createTask.gateway == null) {
      throw new SpecInvalidException("Missing extended property: gateway");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.gateway)) {
      throw new SpecInvalidException("Invalid extended property: gateway: " + createTask.gateway);
    }

    if (createTask.netmask == null) {
      throw new SpecInvalidException("Missing extended property: netmask");
    } else if (!InetAddressValidator.getInstance().isValidInet4Address(createTask.netmask)) {
      throw new SpecInvalidException("Invalid extended property: netmask: " + createTask.netmask);
    }

    if (createTask.etcdIps.size() == 0) {
      throw new SpecInvalidException("Missing extended property: etcd ips");
    }

    // Post createSpec to SwarmClusterCreateTaskService
    Operation operation = dcpClient.postAndWait(
        ServiceUriPaths.SWARM_CLUSTER_CREATE_TASK_SERVICE, createTask);
    return operation.getBody(SwarmClusterCreateTask.class);
  }

  public SwarmClusterCreateTask getSwarmClusterCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = dcpClient.getAndWait(creationTaskLink);
    return operation.getBody(SwarmClusterCreateTask.class);
  }

  public ClusterResizeTask resizeCluster(String clusterId, ClusterResizeOperation resizeOperation) {
    // Translate API ClusterResizeOperation to cluster manager ClusterResizeTask
    ClusterResizeTask resizeTask = new ClusterResizeTask();
    resizeTask.clusterId = clusterId;
    resizeTask.newSlaveCount = resizeOperation.getNewSlaveCount();
    Operation operation = dcpClient.postAndWait(
        ServiceUriPaths.CLUSTER_RESIZE_TASK_SERVICE, resizeTask);
    return operation.getBody(ClusterResizeTask.class);
  }

  public ClusterResizeTask getClusterResizeStatus(String resizeTaskLink)
      throws DocumentNotFoundException {
    Operation operation = dcpClient.getAndWait(resizeTaskLink);
    return operation.getBody(ClusterResizeTask.class);
  }

  public Cluster getCluster(String clusterId) throws ClusterNotFoundException {
    String uri = ClusterServiceFactory.SELF_LINK + "/" + clusterId;
    com.vmware.dcp.common.Operation operation;
    try {
      operation = apiFeDcpClient.getAndWait(uri);
    } catch (DocumentNotFoundException ex) {
      throw new ClusterNotFoundException(clusterId);
    }

    ClusterService.State clusterDocument = operation.getBody(ClusterService.State.class);
    return toApiRepresentation(clusterDocument);
  }

  public ClusterDeleteTask deleteCluster(String clusterId) {
    ClusterDeleteTask deleteTask = new ClusterDeleteTask();
    deleteTask.clusterId = clusterId;

    Operation operation = dcpClient.postAndWait(
        ServiceUriPaths.CLUSTER_DELETE_TASK_SERVICE, deleteTask);

    return operation.getBody(ClusterDeleteTask.class);
  }

  public ClusterDeleteTask getClusterDeletionStatus(String deletionTaskLink)
      throws DocumentNotFoundException {
    Operation operation = dcpClient.getAndWait(deletionTaskLink);
    return operation.getBody(ClusterDeleteTask.class);
  }

  public List<Cluster> getClusters(String projectId) throws ExternalException {
    List<ClusterService.State> clusters = apiFeDcpClient.queryDocuments(
        ClusterService.State.class,
        ImmutableMap.of("projectId", projectId));

    List<Cluster> convertedClusters = new ArrayList<>();
    for (ClusterService.State cluster : clusters) {
      convertedClusters.add(toApiRepresentation(cluster));
    }

    return convertedClusters;
  }

  private void addIpAddressToList(List<String> list, Map<String, String> extendedProperties, String propName)
      throws SpecInvalidException {
    String ipAddress = extendedProperties.get(propName);
    if (ipAddress != null) {
      if (!InetAddressValidator.getInstance().isValidInet4Address(ipAddress)) {
        throw new SpecInvalidException("Invalid extended property: " + propName + " " + ipAddress);
      }
      list.add(ipAddress);
    }
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
