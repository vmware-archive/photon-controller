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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

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
 * Implementation of the availabilityZone operations with DCP as the document store.
 */
@Singleton
public class AvailabilityZoneDcpBackend implements AvailabilityZoneBackend {

  private static final Logger logger = LoggerFactory.getLogger(AvailabilityZoneDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;
  private final TaskBackend taskBackend;

  @Inject
  public AvailabilityZoneDcpBackend(ApiFeDcpRestClient dcpClient, TaskBackend taskBackend) {
    this.dcpClient = dcpClient;
    this.taskBackend = taskBackend;
    this.dcpClient.start();
  }

  @Override
  public TaskEntity createAvailabilityZone(AvailabilityZoneCreateSpec availabilityZone) throws ExternalException {
    if (!findDocumentsByName(Optional.of(availabilityZone.getName())).isEmpty()) {
      throw new NameTakenException(AvailabilityZone.KIND, availabilityZone.getName());
    }

    AvailabilityZoneService.State state = new AvailabilityZoneService.State();
    state.name = availabilityZone.getName();
    state.state = AvailabilityZoneState.READY;
    com.vmware.xenon.common.Operation result = dcpClient.post(AvailabilityZoneServiceFactory.SELF_LINK, state);
    AvailabilityZoneService.State createdState = result.getBody(AvailabilityZoneService.State.class);
    AvailabilityZoneEntity availabilityZoneEntity = convertToEntity(createdState);
    return taskBackend.createCompletedTask(availabilityZoneEntity, Operation.CREATE_AVAILABILITYZONE);
  }

  @Override
  public AvailabilityZone getApiRepresentation(String id) throws ExternalException {
    return convertToEntity(findById(id)).toApiRepresentation();
  }

  @Override
  public List<AvailabilityZone> getListApiRepresentation() throws ExternalException {
    List<AvailabilityZone> resourceList = new ArrayList<>();
    List<AvailabilityZoneEntity> list = getAll();

    for (AvailabilityZoneEntity entity : list) {
      resourceList.add(entity.toApiRepresentation());
    }

    return resourceList;
  }

  @Override
  public AvailabilityZoneEntity getEntityById(String id) throws ExternalException {
    checkNotNull(id);
    return convertToEntity(findById(id));
  }

  @Override
  public List<AvailabilityZoneEntity> getAll() throws ExternalException {
    return findEntitiesByName(Optional.<String>absent());
  }

  @Override
  public TaskEntity prepareAvailabilityZoneDelete(String id) throws ExternalException {
    AvailabilityZoneEntity availabilityZoneEntity = convertToEntity(findById(id));
    EntityStateValidator.validateStateChange(availabilityZoneEntity.getState(),
        AvailabilityZoneState.DELETED, AvailabilityZoneState.PRECONDITION_STATES);

    AvailabilityZoneService.State availabilityZoneState = new AvailabilityZoneService.State();
    availabilityZoneState.state = AvailabilityZoneState.PENDING_DELETE;
    try {
      dcpClient.patch(AvailabilityZoneServiceFactory.SELF_LINK + "/" + availabilityZoneEntity.getId(),
          availabilityZoneState);
    } catch (DocumentNotFoundException e) {
      throw new AvailabilityZoneNotFoundException(availabilityZoneEntity.getId());
    }

    return taskBackend.createCompletedTask(availabilityZoneEntity, Operation.DELETE_AVAILABILITYZONE);
  }

  @Override
  public void tombstone(AvailabilityZoneEntity availabilityZone) throws ExternalException {
    // For tombstone, we'll follow different pattern.
    // Build a service in dcp which gets all PENDING_DELETE availability zones, checks whether
    // there is any host associated with availability zone. If none, then tombstone the availability zone
    // and deletes the availability zone document.
    throw new NotImplementedException();
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
      result = dcpClient.get(AvailabilityZoneServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new AvailabilityZoneNotFoundException(id);
    }

    return result.getBody(AvailabilityZoneService.State.class);
  }

  private List<AvailabilityZoneEntity> findEntitiesByName(Optional<String> name)
      throws ExternalException {
    List<AvailabilityZoneEntity> availabilityZoneEntityList = null;
    List<AvailabilityZoneService.State> availabilityZoneStateList = findDocumentsByName(name);
    if (availabilityZoneStateList != null) {
      availabilityZoneEntityList = new ArrayList<>(availabilityZoneStateList.size());
      for (AvailabilityZoneService.State availabilityZoneState : availabilityZoneStateList) {
        availabilityZoneEntityList.add(convertToEntity(availabilityZoneState));
      }
    }

    return availabilityZoneEntityList;
  }

  private List<AvailabilityZoneService.State> findDocumentsByName(Optional<String> name)
      throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    return dcpClient.queryDocuments(AvailabilityZoneService.State.class, termsBuilder.build());
  }
}
