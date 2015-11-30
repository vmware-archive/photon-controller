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

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the flavor operations with DCP as the document store.
 */
@Singleton
public class FlavorDcpBackend implements FlavorBackend {

  private static final Logger logger = LoggerFactory.getLogger(FlavorDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public FlavorDcpBackend(ApiFeDcpRestClient dcpClient, TaskBackend taskBackend, VmBackend vmBackend,
                          DiskBackend diskBackend, TombstoneBackend tombstoneBackend) {
    this.dcpClient = dcpClient;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.dcpClient.start();
  }

  @Override
  public TaskEntity createFlavor(FlavorCreateSpec flavor) throws ExternalException {

    if (getByNameAndKind(
        Optional.fromNullable(flavor.getName()),
        Optional.fromNullable(flavor.getKind()))
        .isPresent()) {
      throw new NameTakenException(flavor.getKind(), flavor.getName());
    }

    FlavorService.State state = new FlavorService.State();

    state.name = flavor.getName();
    switch (flavor.getKind()) {
      case "ephemeral":
      case "ephemeral-disk":
        state.kind = EphemeralDisk.KIND;
        break;
      case "persistent":
      case "persistent-disk":
        state.kind = PersistentDisk.KIND;
        break;
      case "vm":
        state.kind = Vm.KIND;
        break;
      default:
        throw new IllegalArgumentException(String.format("Flavor kind %s is not allowed.", flavor.getKind()));
    }

    List<FlavorService.State.QuotaLineItem> costEntity = new ArrayList<>();
    for (QuotaLineItem quota : flavor.getCost()) {
      FlavorService.State.QuotaLineItem quotaLineItem = new FlavorService.State.QuotaLineItem();
      quotaLineItem.key = quota.getKey();
      quotaLineItem.value = quota.getValue();
      quotaLineItem.unit = quota.getUnit();
      costEntity.add(quotaLineItem);
    }

    state.cost = costEntity;
    state.state = FlavorState.READY;

    com.vmware.xenon.common.Operation result = dcpClient.post(FlavorServiceFactory.SELF_LINK, state);

    FlavorService.State createdState = result.getBody(FlavorService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setId(id);
    flavorEntity.setName(createdState.name);
    flavorEntity.setKind(createdState.kind);
    flavorEntity.setState(createdState.state);
    return taskBackend.createCompletedTask(flavorEntity, Operation.CREATE_FLAVOR);
  }

  @Override
  public Flavor getApiRepresentation(String id) throws ExternalException {
    return convertToEntity(findById(id)).toApiRepresentation();
  }

  @Override
  public TaskEntity prepareFlavorDelete(String id) throws ExternalException {
    FlavorEntity flavorEntity = convertToEntity(findById(id));
    EntityStateValidator.validateStateChange(flavorEntity.getState(),
        FlavorState.DELETED, FlavorState.PRECONDITION_STATES);

    FlavorService.State flavorState = new FlavorService.State();
    flavorState.state = FlavorState.PENDING_DELETE;
    flavorState.deleteRequestTime = System.currentTimeMillis();
    try {
      dcpClient.patch(FlavorServiceFactory.SELF_LINK + "/" + flavorEntity.getId(),
          flavorState);
    } catch (DocumentNotFoundException e) {
      throw new FlavorNotFoundException(flavorEntity.getId());
    }
    this.tombstone(flavorEntity);

    return taskBackend.createCompletedTask(flavorEntity, Operation.DELETE_FLAVOR);
  }

  @Override
  public List<FlavorEntity> getAll() throws ExternalException {
    return findEntitiesByNameAndKind(Optional.<String>absent(), Optional.<String>absent());
  }

  @Override
  public List<Flavor> filter(Optional<String> name, Optional<String> kind) throws ExternalException {
    List<Flavor> flavorList;

    if (name.isPresent() && kind.isPresent()) {
      Optional<FlavorEntity> flavorEntity = getByNameAndKind(name, kind);
      if (flavorEntity.isPresent()) {
        return ImmutableList.of(flavorEntity.get().toApiRepresentation());
      } else {
        return ImmutableList.of();
      }
    } else {
      flavorList = findFlavorsByNameAndKind(name, kind);
    }

    return flavorList;
  }

  public FlavorEntity getEntityByNameAndKind(String name, String kind) throws ExternalException {
    checkNotNull(name);
    checkNotNull(kind);

    Optional<FlavorEntity> flavorEntity = getByNameAndKind(Optional.of(name), Optional.of(kind));

    if (!flavorEntity.isPresent()) {
      logger.info("FlavorDcpBackend.getEntityByNameAndKind Flavor with name:{} and kind: {} not found.", name, kind);
      throw new FlavorNotFoundException(kind, name);
    }

    return flavorEntity.get();
  }

  @Override
  public FlavorEntity getEntityById(String id) throws ExternalException {
    checkNotNull(id);
    return convertToEntity(findById(id));
  }

  @Override
  public ResourceList<Task> getTasks(String id, Optional<String> state, Optional<Integer> pageSize)
      throws ExternalException {
    FlavorService.State flavor = findById(id);
    return taskBackend.filter(id, flavor.kind, state, pageSize);
  }

  @Override
  public void tombstone(FlavorEntity flavor) throws ExternalException {
    boolean flavorInUse;
    switch (flavor.getKind()) {
      case EphemeralDisk.KIND:
      case PersistentDisk.KIND:
        flavorInUse = diskBackend.existsUsingFlavor(flavor.getId());
        break;
      case Vm.KIND:
        List<Vm> vmsInUse = vmBackend.filterByFlavor(flavor.getId());
        flavorInUse = !vmsInUse.isEmpty();
        break;
      default:
        throw new IllegalArgumentException(String.format("Flavor kind %s is not allowed.", flavor.getKind()));
    }

    if (!flavorInUse) {
      dcpClient.delete(
          FlavorServiceFactory.SELF_LINK + "/" + flavor.getId(),
          new FlavorService.State());

      tombstoneBackend.create(Flavor.KIND, flavor.getId());
      logger.info("flavor {} is cleared", flavor.getId());
    }
  }

  private FlavorEntity convertToEntity(FlavorService.State flavor) {
    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setName(flavor.name);
    flavorEntity.setKind(flavor.kind);
    flavorEntity.setState(flavor.state);

    List<QuotaLineItemEntity> costEntity = new ArrayList<>();

    for (FlavorService.State.QuotaLineItem quota : flavor.cost) {
      QuotaLineItemEntity quotaEntity = new QuotaLineItemEntity();
      quotaEntity.setKey(quota.key);
      quotaEntity.setValue(quota.value);
      quotaEntity.setUnit(quota.unit);
      costEntity.add(quotaEntity);
    }

    flavorEntity.setCost(costEntity);
    flavorEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(flavor.documentSelfLink));

    return flavorEntity;
  }

  private FlavorService.State findById(String id) throws ExternalException {
    com.vmware.xenon.common.Operation result;

    try {
      result = dcpClient.get(FlavorServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new FlavorNotFoundException(id);
    }

    return result.getBody(FlavorService.State.class);
  }

  private Optional<FlavorEntity> getByNameAndKind(Optional<String> name, Optional<String> kind)
      throws ExternalException {
    List<FlavorEntity> flavorEntityList = findEntitiesByNameAndKind(name, kind);
    if (flavorEntityList == null || flavorEntityList.isEmpty()) {
      return Optional.absent();
    }
    return Optional.fromNullable(flavorEntityList.get(0));
  }

  private List<FlavorService.State> findDocumentsByNameAndKind(Optional<String> name, Optional<String> kind)
      throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (kind.isPresent()) {
      termsBuilder.put("kind", kind.get());
    }

    return dcpClient.queryDocuments(FlavorService.State.class, termsBuilder.build());
  }

  private List<FlavorEntity> findEntitiesByNameAndKind(Optional<String> name, Optional<String> kind)
      throws ExternalException {
    List<FlavorEntity> flavorEntityList = null;
    List<FlavorService.State> flavorStateList = findDocumentsByNameAndKind(name, kind);
    if (flavorStateList != null) {
      flavorEntityList = new ArrayList<>(flavorStateList.size());
      for (FlavorService.State flavorState : flavorStateList) {
        flavorEntityList.add(convertToEntity(flavorState));
      }
    }

    return flavorEntityList;
  }

  private List<Flavor> findFlavorsByNameAndKind(Optional<String> name, Optional<String> kind)
      throws ExternalException {
    List<Flavor> flavorList = null;
    List<FlavorService.State> flavorStateList = findDocumentsByNameAndKind(name, kind);
    if (flavorStateList != null) {
      flavorList = new ArrayList<>(flavorStateList.size());

      for (FlavorService.State flavorState : flavorStateList) {
        flavorList.add(convertToEntity(flavorState).toApiRepresentation());
      }
    }

    return flavorList;
  }
}
