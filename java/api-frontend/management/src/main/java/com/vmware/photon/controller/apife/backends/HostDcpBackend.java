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

import com.vmware.photon.controller.api.AvailabilityZoneState;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostAvailabilityZoneAlreadySetException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAvailabilityZoneStateException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Host DCP backend.
 */
@Singleton
public class HostDcpBackend implements HostBackend {

  private static final Logger logger = LoggerFactory.getLogger(HostDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;
  private final TaskBackend taskBackend;
  private final EntityLockBackend entityLockBackend;
  private final DeploymentBackend deploymentBackend;
  private final TombstoneBackend tombstoneBackend;
  private final AvailabilityZoneBackend availabilityZoneBackend;

  @Inject
  public HostDcpBackend(ApiFeDcpRestClient dcpClient, TaskBackend taskBackend,
                        EntityLockBackend entityLockBackend, DeploymentBackend deploymentBackend,
                        TombstoneBackend tombstoneBackend, AvailabilityZoneBackend availabilityZoneBackend) {
    this.dcpClient = dcpClient;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.deploymentBackend = deploymentBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.availabilityZoneBackend = availabilityZoneBackend;
    this.dcpClient.start();
  }

  @Override
  public TaskEntity prepareHostCreate(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException {

    //checking that deployment exists
    deploymentBackend.findById(deploymentId);

    HostEntity host = create(hostCreateSpec);
    logger.info("created Host: {}", host);
    TaskEntity task = createTask(host, deploymentId);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  public TaskEntity prepareHostDelete(String id) throws ExternalException {
    HostEntity host = findById(id);
    logger.info("deleting host: {}", host);
    TaskEntity task = deleteTask(host);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  public TaskEntity resume(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.RESUME_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("resuming host {} to normal mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.RESUME_HOST);
    logger.info("created Task: {}", taskEntity);

    return taskEntity;
  }

  @Override
  public List<Host> listAll() {
    return toApiRepresentations(findDocuments(Optional.<UsageTag>absent()));
  }

  @Override
  public List<Host> filterByUsage(UsageTag usageTag) {
    return toApiRepresentations(findDocuments(Optional.of(usageTag)));
  }

  @Override
  public Host toApiRepresentation(String id) throws HostNotFoundException {
    HostEntity hostEntity = findById(id);
    return toApiRepresentation(hostEntity);
  }

  @Override
  public void updateState(HostEntity entity, HostState state) throws HostNotFoundException {
    HostService.State hostState = new HostService.State();
    hostState.state = state;
    updateHostDocument(entity.getId(), hostState);
    entity.setState(state);
  }

  @Override
  public void tombstone(HostEntity hostEntity) {
    tombstoneBackend.create(hostEntity.getKind(), hostEntity.getId());

    dcpClient.delete(HostServiceFactory.SELF_LINK + "/" + hostEntity.getId(),
        new HostService.State());
    logger.info("HostEntity {} has been cleared", hostEntity.getId());
  }

  @Override
  public TaskEntity setAvailabilityZone(String id, String availabilityZoneId) throws ExternalException {
    HostService.State state = findStateById(id);

    if (state.availabilityZone != null && !state.availabilityZone.isEmpty()) {
      throw new HostAvailabilityZoneAlreadySetException(id, state.availabilityZone);
    }

    checkAvailabilityZoneIsReady(availabilityZoneId);

    HostService.State hostState = new HostService.State();
    hostState.availabilityZone = availabilityZoneId;
    updateHostDocument(id, hostState);

    TaskEntity task = taskBackend.createCompletedTask(toHostEntity(state), Operation.SET_AVAILABILITYZONE);
    return task;
  }

  @Override
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

  private HostService.State findStateById(String id) throws HostNotFoundException {
    com.vmware.xenon.common.Operation result;

    try {
      result = dcpClient.get(HostServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new HostNotFoundException(id);
    }

    return result.getBody(HostService.State.class);
  }

  @Override
  public HostEntity findById(String id) throws HostNotFoundException {
    return toHostEntity(findStateById(id));
  }

  @Override
  public TaskEntity enterMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.ENTER_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} enters maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.ENTER_MAINTENANCE_MODE);
    logger.info("crated Task: {}", taskEntity);
    return taskEntity;
  }

  @Override
  public TaskEntity exitMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.EXIT_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} exits maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.EXIT_MAINTENANCE_MODE);
    logger.info("created Task: {}", taskEntity);

    return taskEntity;
  }

  private HostEntity create(HostCreateSpec hostCreateSpec) throws ExternalException {
    if (hostCreateSpec.getAvailabilityZone() != null && !hostCreateSpec.getAvailabilityZone().isEmpty()) {
      checkAvailabilityZoneIsReady(hostCreateSpec.getAvailabilityZone());
    }

    HostService.State hostState = new HostService.State();

    hostState.state = HostState.CREATING;
    hostState.hostAddress = hostCreateSpec.getAddress();
    hostState.userName = hostCreateSpec.getUsername();
    hostState.password = hostCreateSpec.getPassword();
    hostState.availabilityZone = hostCreateSpec.getAvailabilityZone();
    hostState.metadata = new HashMap<>(hostCreateSpec.getMetadata());
    hostState.usageTags = new HashSet<>(hostCreateSpec.getUsageTags().stream().map(g -> g.name()).collect
        (Collectors.toList()));

    com.vmware.xenon.common.Operation result = dcpClient.post(HostServiceFactory.SELF_LINK, hostState);
    HostService.State createdState = result.getBody(HostService.State.class);
    HostEntity hostEntity = toHostEntity(createdState);
    logger.info("Host {} has been created", hostEntity.getId());

    return hostEntity;
  }

  private TaskEntity createQueuedTaskEntity(HostEntity hostEntity, Operation operation) throws ExternalException {
    TaskEntity taskEntity = taskBackend.createQueuedTask(hostEntity, operation);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, hostEntity, operation);

    taskEntity.getToBeLockedEntityIds().add(hostEntity.getId());
    return taskEntity;
  }

  private TaskEntity createTask(HostEntity host, String deploymentId) throws ExternalException {
    TaskEntity task = createQueuedTaskEntity(host, Operation.CREATE_HOST);

    if (isDeploymentReady(deploymentId)) {
      taskBackend.getStepBackend().createQueuedStep(task, host, Operation.PROVISION_HOST);
    }

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

  private void checkAvailabilityZoneIsReady(String availabilityZoneId) throws ExternalException {
    AvailabilityZoneEntity availabilityZoneEntity = availabilityZoneBackend.getEntityById(availabilityZoneId);
    if (!AvailabilityZoneState.READY.equals(availabilityZoneEntity.getState())) {
      throw new InvalidAvailabilityZoneStateException(String.format("AvailabilityZone %s is in %s state",
          availabilityZoneEntity.getId(), availabilityZoneEntity.getState()));
    }
  }

  private HostEntity toHostEntity(HostService.State hostState) {
    HostEntity hostEntity = new HostEntity();

    String id = ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink);
    hostEntity.setId(id);
    hostEntity.setState(hostState.state);
    hostEntity.setAddress(hostState.hostAddress);
    hostEntity.setUsername(hostState.userName);
    hostEntity.setPassword(hostState.password);
    hostEntity.setAvailabilityZone(hostState.availabilityZone);
    hostEntity.setMetadata(hostState.metadata);
    hostEntity.setEsxVersion(hostState.esxVersion);
    hostEntity.setUsageTags(UsageTagHelper.serialize(hostState.usageTags));

    return hostEntity;
  }

  private List<Host> toApiRepresentations(List<HostService.State> stateList) {
    List<Host> hostList = new ArrayList<>();

    for (HostService.State state : stateList) {
      hostList.add(toApiRepresentation(toHostEntity(state)));
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

  private List<HostService.State> findDocuments(Optional<UsageTag> usageTag) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (usageTag.isPresent()) {
      termsBuilder.put(HostService.State.USAGE_TAGS_KEY, usageTag.get().name());
    }

    return dcpClient.queryDocuments(HostService.State.class, termsBuilder.build());
  }

  private void updateHostDocument(String hostId, HostService.State state) throws HostNotFoundException {
    try {
      dcpClient.patch(HostServiceFactory.SELF_LINK + "/" + hostId, state);
    } catch (DocumentNotFoundException e) {
      throw new HostNotFoundException(hostId);
    }
  }

  private TaskEntity deleteTask(HostEntity hostEntity) throws ExternalException {
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.DELETE_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} in state {}, begin to delete.", hostEntity.getId(), hostEntity.getState());

    TaskEntity task = taskBackend.createQueuedTask(hostEntity, Operation.DELETE_HOST);
    if (hasDeploymentInReadyState()) {
      taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DEPROVISION_HOST);
    }

    taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DELETE_HOST);
    task.getToBeLockedEntityIds().add(hostEntity.getId());
    return task;
  }
}
