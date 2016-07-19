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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.FlavorState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorServiceFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the flavor operations with Xenon as the document store.
 */
@Singleton
public class FlavorXenonBackend implements FlavorBackend {

  private static final Logger logger = LoggerFactory.getLogger(FlavorXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final DiskBackend diskBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public FlavorXenonBackend(ApiFeXenonRestClient xenonClient, TaskBackend taskBackend, VmBackend vmBackend,
                          DiskBackend diskBackend, TombstoneBackend tombstoneBackend) {
    this.xenonClient = xenonClient;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.xenonClient.start();
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

    com.vmware.xenon.common.Operation result = xenonClient.post(FlavorServiceFactory.SELF_LINK, state);

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
    return toApiRepresentation(findById(id));
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
      xenonClient.patch(FlavorServiceFactory.SELF_LINK + "/" + flavorEntity.getId(),
          flavorState);
    } catch (DocumentNotFoundException e) {
      throw new FlavorNotFoundException(flavorEntity.getId());
    }
    this.tombstone(flavorEntity);

    return taskBackend.createCompletedTask(flavorEntity, Operation.DELETE_FLAVOR);
  }

  @Override
  public ResourceList<FlavorEntity> getAll(Optional<Integer> pageSize) throws ExternalException {
    return findEntitiesByNameAndKind(Optional.<String>absent(), Optional.<String>absent(), pageSize);
  }

  @Override
  public ResourceList<Flavor> filter(Optional<String> name, Optional<String> kind, Optional<Integer> pageSize)
          throws ExternalException {
    return findFlavorsByNameAndKind(name, kind, pageSize);
  }

  public FlavorEntity getEntityByNameAndKind(String name, String kind) throws ExternalException {
    checkNotNull(name);
    checkNotNull(kind);

    Optional<FlavorEntity> flavorEntity = getByNameAndKind(Optional.of(name), Optional.of(kind));

    if (!flavorEntity.isPresent()) {
      logger.info("FlavorxenonBackend.getEntityByNameAndKind Flavor with name:{} and kind: {} not found.", name, kind);
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
      xenonClient.delete(
          FlavorServiceFactory.SELF_LINK + "/" + flavor.getId(),
          new FlavorService.State());

      tombstoneBackend.create(Flavor.KIND, flavor.getId());
      logger.info("flavor {} is cleared", flavor.getId());
    }
  }

  public ResourceList<Flavor> getFlavorsPage(String pageLink) throws PageExpiredException{
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
            FlavorService.State.class, queryResult, state -> toApiRepresentation(state));
  }

  public Flavor toApiRepresentation(FlavorService.State flavorDocument) {
    Flavor flavor = new Flavor();
    String id = ServiceUtils.getIDFromDocumentSelfLink(flavorDocument.documentSelfLink);
    flavor.setId(id);
    flavor.setName(flavorDocument.name);
    flavor.setKind(flavorDocument.kind);
    flavor.setState(flavorDocument.state);

    List<QuotaLineItem> costs = new ArrayList<>();

    if (flavorDocument.cost != null && !flavorDocument.cost.isEmpty()) {
      for (FlavorService.State.QuotaLineItem costEntity : flavorDocument.cost) {
        costs.add(new QuotaLineItem(costEntity.key, costEntity.value, costEntity.unit));
      }
    }

    flavor.setCost(costs);

    Set<String> tags = new HashSet<>();
    if (flavorDocument.tags != null && !flavorDocument.tags.isEmpty()) {
      tags.addAll(flavorDocument.tags);
    }

    flavor.setTags(tags);

    return flavor;
  }

  private FlavorEntity convertToEntity(FlavorService.State flavor) {
    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setName(flavor.name);
    flavorEntity.setKind(flavor.kind);
    flavorEntity.setState(flavor.state);

    List<QuotaLineItemEntity> costEntity = new ArrayList<>();

    if (flavor.cost != null && !flavor.cost.isEmpty()) {
      for (FlavorService.State.QuotaLineItem quota : flavor.cost) {
        QuotaLineItemEntity quotaEntity = new QuotaLineItemEntity();
        quotaEntity.setKey(quota.key);
        quotaEntity.setValue(quota.value);
        quotaEntity.setUnit(quota.unit);
        costEntity.add(quotaEntity);
      }
    }

    flavorEntity.setCost(costEntity);
    flavorEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(flavor.documentSelfLink));

    return flavorEntity;
  }

  private FlavorService.State findById(String id) throws ExternalException {
    com.vmware.xenon.common.Operation result;

    try {
      result = xenonClient.get(FlavorServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new FlavorNotFoundException(id);
    }

    return result.getBody(FlavorService.State.class);
  }

  private Optional<FlavorEntity> getByNameAndKind(Optional<String> name, Optional<String> kind)
      throws ExternalException {
    ResourceList<FlavorEntity> flavorEntityList = findEntitiesByNameAndKind(name, kind, Optional.<Integer>absent());
    if (flavorEntityList == null || flavorEntityList.getItems() == null || flavorEntityList.getItems().isEmpty()) {
      return Optional.absent();
    }
    return Optional.fromNullable(flavorEntityList.getItems().get(0));
  }

  private ServiceDocumentQueryResult findDocumentsByNameAndKind(Optional<String> name, Optional<String> kind,
                                                                       Optional<Integer> pageSize)
          throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (kind.isPresent()) {
      termsBuilder.put("kind", kind.get());
    }

    return xenonClient.queryDocuments(
            FlavorService.State.class, termsBuilder.build(), pageSize, true);
  }

  private ResourceList<FlavorEntity> findEntitiesByNameAndKind(Optional<String> name, Optional<String> kind,
                                                               Optional<Integer> pageSize)
      throws ExternalException {
    ServiceDocumentQueryResult queryResult = findDocumentsByNameAndKind(name, kind, pageSize);

    return PaginationUtils.xenonQueryResultToResourceList(FlavorService.State.class, queryResult,
            state -> convertToEntity(state));
  }

  private ResourceList<Flavor> findFlavorsByNameAndKind(Optional<String> name, Optional<String> kind,
                                                Optional<Integer> pageSize)
      throws ExternalException {
    ServiceDocumentQueryResult queryResult = findDocumentsByNameAndKind(name, kind, pageSize);

    return PaginationUtils.xenonQueryResultToResourceList(FlavorService.State.class, queryResult,
            state -> toApiRepresentation(state));
  }
}
