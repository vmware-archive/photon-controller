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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.BackendTaskExecutor;
import com.vmware.photon.controller.api.frontend.backends.ClusterBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.ClusterResizeOperation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Frontend client for cluster tasks.
 */
@Singleton
public class ClusterFeClient {
  private static final Logger logger = LoggerFactory.getLogger(ClusterFeClient.class);

  private final ClusterBackend clusterBackend;
  private final TaskBackend taskBackend;
  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;

  @Inject
  public ClusterFeClient(ClusterBackend clusterBackend,
                         TaskBackend taskBackend,
                         TaskCommandFactory commandFactory,
                         @BackendTaskExecutor ExecutorService executor) {
    this.clusterBackend = clusterBackend;
    this.taskBackend = taskBackend;
    this.commandFactory = commandFactory;
    this.executor = executor;
  }

  public Task create(String projectId, ClusterCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = clusterBackend.create(projectId, spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task resize(String clusterId, ClusterResizeOperation operation) throws ExternalException {
    TaskEntity taskEntity = clusterBackend.resize(clusterId, operation);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Cluster get(String id) throws ExternalException {
    return clusterBackend.get(id);
  }

  public ResourceList<Cluster> find(String projectId, Optional<Integer> pageSize) throws ExternalException {
    return clusterBackend.find(projectId, pageSize);
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = clusterBackend.delete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public ResourceList<Vm> findVms(String clustertId, Optional<Integer> pageSize) throws ExternalException {
    return clusterBackend.findVms(clustertId, pageSize);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return clusterBackend.getVmsPage(pageLink);
  }

  public ResourceList<Cluster> getClustersPage(String pageLink) throws ExternalException {
    return clusterBackend.getClustersPage(pageLink);
  }
}
