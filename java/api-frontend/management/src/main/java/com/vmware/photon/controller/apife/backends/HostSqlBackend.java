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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.HostDao;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Host service backend.
 */
@Singleton
public class HostSqlBackend implements HostBackend {

  private static final Logger logger = LoggerFactory.getLogger(HostSqlBackend.class);

  private final TaskBackend taskBackend;
  private final DeploymentBackend deploymentBackend;
  private final EntityLockBackend entityLockBackend;
  private final TombstoneBackend tombstoneBackend;
  private final HostDao hostDao;

  @Inject
  public HostSqlBackend(TaskBackend taskBackend,
                        DeploymentBackend deploymentBackend,
                        EntityLockBackend entityLockBackend,
                        TombstoneBackend tombstoneBackend,
                        HostDao hostDao) {
    this.taskBackend = taskBackend;
    this.deploymentBackend = deploymentBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.hostDao = hostDao;
  }

  @Transactional
  public TaskEntity prepareHostCreate(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException {

    //checking that deployment exists
    deploymentBackend.findById(deploymentId);

    HostEntity host = create(hostCreateSpec);
    logger.info("created Host: {}", host);
    TaskEntity task = createTask(host, deploymentId);
    logger.info("created Task: {}", task);
    return task;
  }

  @Transactional
  public TaskEntity prepareHostDelete(String id) throws ExternalException {
    HostEntity host = findById(id);
    logger.info("deleting host: {}", host);
    TaskEntity task = deleteTask(host);
    logger.info("created Task: {}", task);
    return task;
  }

  @Transactional
  public TaskEntity resume(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.RESUME_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("resuming host {} to normal mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.RESUME_HOST);
    logger.info("created Task: {}", taskEntity);

    return taskEntity;
  }

  @Transactional
  public List<Host> listAll() {
    return toApiRepresentation(hostDao.listAll());
  }

  @Transactional
  public List<Host> filterByUsage(UsageTag usageTag) {
    return toApiRepresentation(hostDao.listAllByUsage(usageTag));
  }

  @Transactional
  public Host toApiRepresentation(String id) throws HostNotFoundException {
    HostEntity hostEntity = findById(id);
    return toApiRepresentation(hostEntity);
  }

  @Transactional
  public void updateState(HostEntity entity, HostState state) {
    entity.setState(state);
    hostDao.update(entity);
  }

  @Transactional
  public void tombstone(HostEntity hostEntity) {
    tombstoneBackend.create(hostEntity.getKind(), hostEntity.getId());
    hostDao.delete(hostEntity);
  }

  @Transactional
  public TaskEntity suspend(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.SUSPEND_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("putting host {} in suspended mode.");
    hostEntity.setState(HostState.SUSPENDED);
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.SUSPEND_HOST);
    logger.info("created Task: {}", taskEntity);

    return taskEntity;
  }

  @Transactional
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    HostEntity hostEntity = findById(id);
    return taskBackend.filter(hostEntity.getId(), hostEntity.getKind(), state);
  }

  @Transactional
  public HostEntity findById(String id) throws HostNotFoundException {
    Optional<HostEntity> hostEntity = hostDao.findById(id);

    if (hostEntity.isPresent()) {
      return hostEntity.get();
    }

    throw new HostNotFoundException(id);
  }

  @Transactional
  public TaskEntity enterMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.ENTER_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("putting host {} in maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.ENTER_MAINTENANCE_MODE);
    logger.info("crated Task: {}", taskEntity);
    return taskEntity;
  }

  @Transactional
  public TaskEntity exitMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.EXIT_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("putting host {} in maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.EXIT_MAINTENANCE_MODE);
    logger.info("created Task: {}", taskEntity);

    return taskEntity;
  }

  private List<Host> toApiRepresentation(List<HostEntity> hostEntityList) {
    List<Host> hostList = new ArrayList<>();

    for (HostEntity entity : hostEntityList) {
      hostList.add(toApiRepresentation(entity));
    }

    return hostList;
  }

  private Host toApiRepresentation(HostEntity hostEntity) {
    Host host = new Host();

    host.setState(hostEntity.getState());
    host.setId(hostEntity.getId());
    host.setAddress(hostEntity.getAddress());
    host.setUsername(hostEntity.getUsername());
    host.setPassword(hostEntity.getPassword());
    host.setAvailabilityZone(hostEntity.getAvailabilityZone());
    host.setEsxVersion(hostEntity.getEsxVersion());
    host.setUsageTags(UsageTagHelper.deserialize(hostEntity.getUsageTags()));
    host.setMetadata(hostEntity.getMetadata());

    return host;
  }

  private TaskEntity createQueuedTaskEntity(HostEntity hostEntity, Operation operation) throws ExternalException {
    TaskEntity taskEntity = taskBackend.createQueuedTask(hostEntity, operation);
    StepEntity stepEntity = taskBackend.getStepBackend().createQueuedStep(taskEntity, hostEntity, operation);

    entityLockBackend.setStepLock(hostEntity, stepEntity);
    return taskEntity;
  }

  private HostEntity create(HostCreateSpec hostCreateSpec) {
    HostEntity host = new HostEntity();
    host.setState(HostState.CREATING);
    host.setAddress(hostCreateSpec.getAddress());
    host.setUsername(hostCreateSpec.getUsername());
    host.setPassword(hostCreateSpec.getPassword());
    host.setAvailabilityZone(hostCreateSpec.getAvailabilityZone());
    host.setMetadata(hostCreateSpec.getMetadata());
    host.setUsageTags(UsageTagHelper.serialize(hostCreateSpec.getUsageTags()));
    hostDao.create(host);
    return host;
  }

  private TaskEntity createTask(HostEntity host, String deploymentId) throws ExternalException {
    TaskEntity task = createQueuedTaskEntity(host, Operation.CREATE_HOST);

    if (isDeploymentReady(deploymentId)) {
      taskBackend.getStepBackend().createQueuedStep(task, host, Operation.PROVISION_HOST);
    }

    return task;
  }

  private TaskEntity deleteTask(HostEntity hostEntity) throws ExternalException {
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.DELETE_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} in state {}, begin to delete.", hostEntity.getId(), hostEntity.getState());

    TaskEntity task = taskBackend.createQueuedTask(hostEntity, Operation.DELETE_HOST);
    if (hasDeploymentInReadyState()) {
      taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DEPROVISION_HOST);
    }
    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DELETE_HOST);
    entityLockBackend.setStepLock(hostEntity, step);

    return task;
  }

  private boolean hasDeploymentInReadyState() {
    List<Deployment> deployments = deploymentBackend.getAll();
    return !deployments.isEmpty() &&
        DeploymentState.READY.equals(deployments.get(0).getState());
  }

  private boolean isDeploymentReady(String deploymentId) throws DeploymentNotFoundException {
    DeploymentEntity deploymentEntity = deploymentBackend.findById(deploymentId);
    return deploymentEntity.getState().equals(DeploymentState.READY);
  }
}
