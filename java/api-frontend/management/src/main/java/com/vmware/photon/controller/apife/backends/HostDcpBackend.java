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
import com.vmware.photon.controller.api.HostDatastore;
import com.vmware.photon.controller.api.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.HostDatastoreEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostAvailabilityZoneAlreadySetException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAvailabilityZoneStateException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Host DCP backend.
 */
@Singleton
public class HostDcpBackend implements HostBackend {

  private static final Logger logger = LoggerFactory.getLogger(HostDcpBackend.class);

  private final ApiFeXenonRestClient dcpClient;
  private final TaskBackend taskBackend;
  private final EntityLockBackend entityLockBackend;
  private final DeploymentBackend deploymentBackend;
  private final TombstoneBackend tombstoneBackend;
  private final AvailabilityZoneBackend availabilityZoneBackend;

  private final DeployerClient deployerClient;

  @Inject
  public HostDcpBackend(ApiFeXenonRestClient dcpClient, DeployerClient deployerClient, TaskBackend taskBackend,
                        EntityLockBackend entityLockBackend, DeploymentBackend deploymentBackend,
                        TombstoneBackend tombstoneBackend, AvailabilityZoneBackend availabilityZoneBackend) {
    this.dcpClient = dcpClient;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.deploymentBackend = deploymentBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.availabilityZoneBackend = availabilityZoneBackend;
    this.deployerClient = deployerClient;
    this.dcpClient.start();
  }

  public DeployerClient getDeployerClient() {
    return deployerClient;
  }

  @Override
  public TaskEntity prepareHostCreate(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException {

    //checking that deployment exists
    deploymentBackend.findById(deploymentId);

    HostEntity host = create(hostCreateSpec);
    logger.info("created Host: {}", host);
    TaskEntity task = createTask(host, deploymentId);
    return task;
  }

  @Override
  public TaskEntity prepareHostDelete(String id) throws ExternalException {
    HostEntity hostEntity = findById(id);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.DELETE_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("deleting host: {}", hostEntity);
    TaskEntity task = deleteTask(hostEntity);
    return task;
  }



  @Override
  public HostEntity findById(String id) throws HostNotFoundException {
    return toHostEntity(findStateById(id));
  }

  @Override
  public ResourceList<Host> listAll(Optional<Integer> pageSize) {

    return findDocuments(new ImmutableMap.Builder<>(), pageSize);
  }

  @Override
  public ResourceList<Host> getHostsPage(String pageLink) throws PageExpiredException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = dcpClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
        HostService.State.class, queryResult, state -> toApiRepresentation(state));
  }

  @Override
  public ResourceList<Host> filterByUsage(UsageTag usageTag, Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(HostService.State.USAGE_TAGS_KEY, usageTag.name());

    return findDocuments(termsBuilder, pageSize);
  }

  @Override
  public ResourceList<Host> filterByAddress(String address, Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(HostService.State.FIELD_NAME_HOST_ADDRESS, address);

    return findDocuments(termsBuilder, pageSize);
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
  public void updateAvailabilityZone(HostEntity entity) throws HostNotFoundException {
    HostService.State hostState = new HostService.State();
    hostState.availabilityZoneId = entity.getAvailabilityZone();
    updateHostDocument(entity.getId(), hostState);
  }

  @Override
  public void tombstone(HostEntity hostEntity) {
    tombstoneBackend.create(hostEntity.getKind(), hostEntity.getId());

    dcpClient.delete(HostServiceFactory.SELF_LINK + "/" + hostEntity.getId(),
        new HostService.State());
    logger.info("HostEntity {} has been cleared", hostEntity.getId());
  }

  @Override
  public TaskEntity setAvailabilityZone(String id, HostSetAvailabilityZoneOperation hostSetAvailabilityZoneOperation)
      throws ExternalException {
    HostService.State state = findStateById(id);

    if (state.availabilityZoneId != null && !state.availabilityZoneId.isEmpty()) {
      throw new HostAvailabilityZoneAlreadySetException(id, state.availabilityZoneId);
    }

    checkAvailabilityZoneIsReady(hostSetAvailabilityZoneOperation.getAvailabilityZoneId());

    state.availabilityZoneId = hostSetAvailabilityZoneOperation.getAvailabilityZoneId();
    TaskEntity taskEntity = createQueuedTaskEntity(toHostEntity(state), Operation.SET_AVAILABILITYZONE);
    return taskEntity;
  }

  @Override
  public TaskEntity suspend(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.SUSPEND_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("putting host {} in suspended mode.");
    hostEntity.setState(HostState.SUSPENDED);
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.SUSPEND_HOST);

    return taskEntity;
  }

  @Override
  public TaskEntity resume(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.RESUME_HOST, HostState.OPERATION_PREREQ_STATE);
    logger.info("resuming host {} to normal mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.RESUME_HOST);

    return taskEntity;
  }

  @Override
  public TaskEntity enterMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.ENTER_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} enters maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.ENTER_MAINTENANCE_MODE);
    return taskEntity;
  }

  @Override
  public TaskEntity exitMaintenance(String hostId) throws ExternalException {
    HostEntity hostEntity = findById(hostId);
    EntityStateValidator.validateOperationState(hostEntity, hostEntity.getState(),
        Operation.EXIT_MAINTENANCE_MODE, HostState.OPERATION_PREREQ_STATE);
    logger.info("host {} exits maintenance mode.");
    TaskEntity taskEntity = createQueuedTaskEntity(hostEntity, Operation.EXIT_MAINTENANCE_MODE);

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

  private HostEntity create(HostCreateSpec hostCreateSpec) throws ExternalException {
    if (hostCreateSpec.getAvailabilityZone() != null && !hostCreateSpec.getAvailabilityZone().isEmpty()) {
      checkAvailabilityZoneIsReady(hostCreateSpec.getAvailabilityZone());
    }

    HostService.State hostState = new HostService.State();

    hostState.state = HostState.CREATING;
    hostState.hostAddress = hostCreateSpec.getAddress();
    hostState.userName = hostCreateSpec.getUsername();
    hostState.password = hostCreateSpec.getPassword();
    hostState.availabilityZoneId = hostCreateSpec.getAvailabilityZone();
    hostState.metadata = new HashMap<>(hostCreateSpec.getMetadata());
    hostState.usageTags = new HashSet<>(hostCreateSpec.getUsageTags().stream().map(g -> g.name()).collect
        (Collectors.toList()));

    com.vmware.xenon.common.Operation result = dcpClient.post(HostServiceFactory.SELF_LINK, hostState);
    HostService.State createdState = result.getBody(HostService.State.class);
    HostEntity hostEntity = toHostEntity(createdState);
    logger.info("Host {} has been created", hostEntity);

    return hostEntity;
  }

  private TaskEntity createQueuedTaskEntity(HostEntity hostEntity, Operation operation) throws ExternalException {
    TaskEntity taskEntity = taskBackend.createQueuedTask(hostEntity, operation);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, hostEntity, operation);

    taskEntity.getToBeLockedEntities().add(hostEntity);
    return taskEntity;
  }

  private TaskEntity createTask(HostEntity host, String deploymentId) throws ExternalException {
    TaskEntity task = createQueuedTaskEntity(host, Operation.CREATE_HOST);
    taskBackend.getStepBackend().createQueuedStep(task, Operation.QUERY_HOST_TASK_RESULT);
    if (isDeploymentReady(deploymentId)) {
      taskBackend.getStepBackend().createQueuedStep(task, host, Operation.PROVISION_HOST);
      taskBackend.getStepBackend().createQueuedStep(task, host, Operation.QUERY_PROVISION_HOST_TASK_RESULT);
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
    hostEntity.setAvailabilityZone(hostState.availabilityZoneId);
    hostEntity.setMetadata(hostState.metadata);
    hostEntity.setEsxVersion(hostState.esxVersion);
    hostEntity.setUsageTags(UsageTagHelper.serialize(hostState.usageTags));

    if (null != hostState.datastoreServiceLinks && 0 < hostState.datastoreServiceLinks.size()) {
      hostEntity.setDatastores(new ArrayList<>());
      for (Map.Entry<String, String> entry : hostState.datastoreServiceLinks.entrySet()) {
        HostDatastoreEntity datastore = new HostDatastoreEntity();
        datastore.setDatastoreId(ServiceUtils.getIDFromDocumentSelfLink(entry.getValue()));
        datastore.setMountPoint(entry.getKey());
        datastore.setImageDatastore(hostState.reportedImageDatastores.contains(datastore.getDatastoreId()));

        hostEntity.getDatastores().add(datastore);
      }
    }

    return hostEntity;
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

    if (null != hostEntity.getDatastores() && 0 < hostEntity.getDatastores().size()) {
      host.setDatastores(new ArrayList<>());
      for (HostDatastoreEntity datastoreEntity : hostEntity.getDatastores()) {
        host.getDatastores().add(
            new HostDatastore(
                datastoreEntity.getDatastoreId(),
                datastoreEntity.getMountPoint(),
                datastoreEntity.isImageDatastore()));
      }
    }

    return host;
  }

  private Host toApiRepresentation(HostService.State hostState) {
    Host host = new Host();
    String id = ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink);
    host.setState(hostState.state);
    host.setId(id);
    host.setAddress(hostState.hostAddress);
    host.setUsername(hostState.userName);
    host.setPassword(hostState.password);
    host.setAvailabilityZone(hostState.availabilityZoneId);
    host.setEsxVersion(hostState.esxVersion);
    host.setUsageTags(UsageTagHelper.deserialize(UsageTagHelper.serialize(hostState.usageTags)));
    host.setMetadata(hostState.metadata);

    if (null != hostState.datastoreServiceLinks && 0 < hostState.datastoreServiceLinks.size()) {
      host.setDatastores(new ArrayList<>());
      for (Map.Entry<String, String> entry : hostState.datastoreServiceLinks.entrySet()) {
        String datastoreId = ServiceUtils.getIDFromDocumentSelfLink(entry.getValue());
        host.getDatastores().add(
            new HostDatastore(
                datastoreId,
                entry.getKey(),
                hostState.reportedImageDatastores.contains(datastoreId)));
      }
    }

    return host;
  }

  private ResourceList<Host> findDocuments(
      ImmutableMap.Builder<String, String> termsBuilder, Optional<Integer> pageSize) {
    ServiceDocumentQueryResult queryResult = dcpClient.queryDocuments(
        HostService.State.class, termsBuilder.build(), pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(HostService.State.class, queryResult,
        state -> toApiRepresentation(state));
  }

  private void updateHostDocument(String hostId, HostService.State state) throws HostNotFoundException {
    try {
      dcpClient.patch(HostServiceFactory.SELF_LINK + "/" + hostId, state);
    } catch (DocumentNotFoundException e) {
      throw new HostNotFoundException(hostId);
    }
  }

  private TaskEntity deleteTask(HostEntity hostEntity) throws ExternalException {
    logger.info("host {} in state {}, begin to delete.", hostEntity.getId(), hostEntity.getState());

    TaskEntity task = taskBackend.createQueuedTask(hostEntity, Operation.DELETE_HOST);
    if (hasDeploymentInReadyState()) {
      taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DEPROVISION_HOST);
      taskBackend.getStepBackend().createQueuedStep(task, Operation.QUERY_DEPROVISION_HOST_TASK_RESULT);
    }

    taskBackend.getStepBackend().createQueuedStep(task, hostEntity, Operation.DELETE_HOST);
    task.getToBeLockedEntities().add(hostEntity);
    return task;
  }
}
