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

import com.vmware.photon.controller.api.AvailabilityZone;
import com.vmware.photon.controller.api.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.AvailabilityZoneState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the availabilityZone operations with Xenon as the document store.
 */
@Singleton
public class AvailabilityZoneXenonBackend implements AvailabilityZoneBackend {

  private static final Logger logger = LoggerFactory.getLogger(AvailabilityZoneXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final TaskBackend taskBackend;

  @Inject
  public AvailabilityZoneXenonBackend(ApiFeXenonRestClient xenonClient, TaskBackend taskBackend) {
    this.xenonClient = xenonClient;
    this.taskBackend = taskBackend;
    this.xenonClient.start();
  }

  @Override
  public TaskEntity createAvailabilityZone(AvailabilityZoneCreateSpec availabilityZone) throws ExternalException {
    AvailabilityZoneService.State state = new AvailabilityZoneService.State();
    state.name = availabilityZone.getName();
    state.state = AvailabilityZoneState.READY;
    com.vmware.xenon.common.Operation result = xenonClient.post(AvailabilityZoneServiceFactory.SELF_LINK, state);
    AvailabilityZoneService.State createdState = result.getBody(AvailabilityZoneService.State.class);
    AvailabilityZoneEntity availabilityZoneEntity = convertToEntity(createdState);
    return taskBackend.createCompletedTask(availabilityZoneEntity, Operation.CREATE_AVAILABILITYZONE);
  }

  @Override
  public AvailabilityZone getApiRepresentation(String id) throws ExternalException {
    return convertToEntity(findById(id)).toApiRepresentation();
  }

  @Override
  public ResourceList<AvailabilityZone> getListApiRepresentation(Optional<Integer> pageSize)
      throws ExternalException {

    List<AvailabilityZone> result = new ArrayList<>();
    ResourceList<AvailabilityZoneEntity> list = getAll(pageSize);
    list.getItems().forEach(entity -> result.add(entity.toApiRepresentation()));

    return new ResourceList<>(result, list.getNextPageLink(), list.getPreviousPageLink());
  }

  @Override
  public AvailabilityZoneEntity getEntityById(String id) throws ExternalException {
    checkNotNull(id);
    return convertToEntity(findById(id));
  }

  @Override
  public ResourceList<AvailabilityZoneEntity> getAll(Optional<Integer> pageSize) throws ExternalException {
    return findEntitiesByName(Optional.<String>absent(), pageSize);
  }

  @Override
  public TaskEntity prepareAvailabilityZoneDelete(String id) throws ExternalException {
    AvailabilityZoneEntity availabilityZoneEntity = convertToEntity(findById(id));
    EntityStateValidator.validateStateChange(availabilityZoneEntity.getState(),
        AvailabilityZoneState.DELETED, AvailabilityZoneState.PRECONDITION_STATES);

    AvailabilityZoneService.State availabilityZoneState = new AvailabilityZoneService.State();
    availabilityZoneState.state = AvailabilityZoneState.PENDING_DELETE;
    try {
      xenonClient.patch(AvailabilityZoneServiceFactory.SELF_LINK + "/" + availabilityZoneEntity.getId(),
          availabilityZoneState);
    } catch (DocumentNotFoundException e) {
      throw new AvailabilityZoneNotFoundException(availabilityZoneEntity.getId());
    }

    return taskBackend.createCompletedTask(availabilityZoneEntity, Operation.DELETE_AVAILABILITYZONE);
  }

  @Override
  public void tombstone(AvailabilityZoneEntity availabilityZone) throws ExternalException {
    // For tombstone, we'll follow different pattern.
    // Build a service in Xenon which gets all PENDING_DELETE availability zones, checks whether
    // there is any host associated with availability zone. If none, then tombstone the availability zone
    // and deletes the availability zone document.
    throw new NotImplementedException();
  }

  @Override
  public ResourceList<AvailabilityZone> getPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(AvailabilityZoneService.State.class, queryResult,
        state -> convertToEntity(state).toApiRepresentation());
  }

  private AvailabilityZoneEntity convertToEntity(AvailabilityZoneService.State availabilityZone) {
    AvailabilityZoneEntity availabilityZoneEntity = new AvailabilityZoneEntity();
    availabilityZoneEntity.setName(availabilityZone.name);
    availabilityZoneEntity.setState(availabilityZone.state);
    availabilityZoneEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(availabilityZone.documentSelfLink));
    return availabilityZoneEntity;
  }

  private AvailabilityZoneService.State findById(String id) throws ExternalException {
    com.vmware.xenon.common.Operation result;

    try {
      result = xenonClient.get(AvailabilityZoneServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new AvailabilityZoneNotFoundException(id);
    }

    return result.getBody(AvailabilityZoneService.State.class);
  }

  private ResourceList<AvailabilityZoneEntity> findEntitiesByName(Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(AvailabilityZoneService.State.class,
        termsBuilder.build(), pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(AvailabilityZoneService.State.class, queryResult,
        state -> convertToEntity(state));
  }
}
