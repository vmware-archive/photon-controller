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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.ClusterResizeOperation;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.backends.clients.ClusterManagerClient;
import com.vmware.photon.controller.apife.commands.steps.ClusterDeleteStepCmd;
import com.vmware.photon.controller.apife.commands.steps.ClusterResizeStepCmd;
import com.vmware.photon.controller.apife.commands.steps.KubernetesClusterCreateStepCmd;
import com.vmware.photon.controller.apife.commands.steps.MesosClusterCreateStepCmd;
import com.vmware.photon.controller.apife.commands.steps.SwarmClusterCreateStepCmd;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ClusterNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.apife.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URISyntaxException;

/**
 * Call cluster manager Xenon services to fulfill cluster operations.
 */
@Singleton
public class ClusterBackend {
  private static final Logger logger = LoggerFactory.getLogger(ClusterBackend.class);

  private final ClusterManagerClient clusterManagerClient;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;

  @Inject
  public ClusterBackend(ClusterManagerClient clusterManagerClient,
                        TaskBackend taskBackend, VmBackend vmBackend)
      throws URISyntaxException {
    this.clusterManagerClient = clusterManagerClient;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
  }

  public ClusterManagerClient getClusterManagerClient() {
    return clusterManagerClient;
  }

  // Kick off the creation of a cluster.
  public TaskEntity create(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException{
    switch (spec.getType()) {
      case KUBERNETES:
        return createKubernetesCluster(projectId, spec);
      case MESOS:
        return createMesosCluster(projectId, spec);
      case SWARM:
        return createSwarmCluster(projectId, spec);
      default:
        throw new SpecInvalidException("Unsupported cluster type: " + spec.getType());
    }
  }

  // Kick off cluster resize operation.
  public TaskEntity resize(String clusterId, ClusterResizeOperation resizeOperation)
      throws TaskNotFoundException, ClusterNotFoundException {
    // Check if a cluster exists for the passed clusterId
    checkClusterId(clusterId);

    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.RESIZE_CLUSTER);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.RESIZE_CLUSTER_INITIATE);

    // Pass clusterId and resizeOperation to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(ClusterResizeStepCmd.CLUSTER_ID_RESOURCE_KEY, clusterId);
    initiateStep.createOrUpdateTransientResource(ClusterResizeStepCmd.RESIZE_OPERATION_RESOURCE_KEY, resizeOperation);

    // Dummy steps that are mapped to KubernetesClusterResizeTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESIZE_CLUSTER_INITIALIZE_CLUSTER);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESIZE_CLUSTER_RESIZE);

    // Send acknowledgement to client
    return taskEntity;
  }

  public Cluster get(String clusterId) throws ClusterNotFoundException {
    return clusterManagerClient.getCluster(clusterId);
  }

  public ResourceList<Cluster> find(String projectId, Optional<Integer> pageSize) throws ExternalException {
    return clusterManagerClient.getClusters(projectId, pageSize);
  }

  public TaskEntity delete(String clusterId) throws TaskNotFoundException, ClusterNotFoundException {
    // Check if a cluster exists for the passed clusterId
    checkClusterId(clusterId);

    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.DELETE_CLUSTER);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.DELETE_CLUSTER_INITIATE);

    // Pass clusterId to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(ClusterDeleteStepCmd.CLUSTER_ID_RESOURCE_KEY, clusterId);

    // Dummy steps that are mapped to ClusterDeleteTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_CLUSTER_UPDATE_CLUSTER_DOCUMENT);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_CLUSTER_DELETE_VMS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_CLUSTER_DOCUMENT);

    // Send acknowledgement to client
    return taskEntity;
  }

  public ResourceList<Vm> findVms(String clusterId, Optional<Integer> pageSize) throws ExternalException {
    // Get projectId for cluster
    Cluster cluster = clusterManagerClient.getCluster(clusterId);

    // Find VMs by tag
    Tag clusterIdTag = new Tag(ClusterUtil.createClusterTag(clusterId));
    return vmBackend.filterByTag(cluster.getProjectId(), clusterIdTag, pageSize);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return vmBackend.getVmsPage(pageLink);
  }

  public ResourceList<Cluster> getClustersPage(String pageLink) throws ExternalException {
    return clusterManagerClient.getClustersPages(pageLink);
  }

  private void checkClusterId(String clusterId) throws ClusterNotFoundException {
    Cluster cluster = clusterManagerClient.getCluster(clusterId);
    checkNotNull(cluster);
  }

  private TaskEntity createKubernetesCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.CREATE_CLUSTER);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_KUBERNETES_CLUSTER_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(KubernetesClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(KubernetesClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to KubernetesClusterCreateTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_CLUSTER_SETUP_ETCD);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_CLUSTER_SETUP_MASTER);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_CLUSTER_SETUP_SLAVES);

    return taskEntity;
  }

  private TaskEntity createMesosCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.CREATE_CLUSTER);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_MESOS_CLUSTER_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(MesosClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(MesosClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to MesosClusterCreateTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_CLUSTER_SETUP_ZOOKEEPERS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_CLUSTER_SETUP_MASTERS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_CLUSTER_SETUP_MARATHON);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_CLUSTER_SETUP_SLAVES);

    return taskEntity;
  }

  private TaskEntity createSwarmCluster(String projectId, ClusterCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(
        null, Operation.CREATE_CLUSTER);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_SWARM_CLUSTER_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(SwarmClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(SwarmClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to SwarmClusterCreateTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_CLUSTER_SETUP_ETCD);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_CLUSTER_SETUP_MASTER);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_CLUSTER_SETUP_SLAVES);

    return taskEntity;
  }
}
