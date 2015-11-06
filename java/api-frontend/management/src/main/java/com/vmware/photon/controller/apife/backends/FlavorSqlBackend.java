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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.EphemeralDiskDao;
import com.vmware.photon.controller.apife.db.dao.FlavorDao;
import com.vmware.photon.controller.apife.db.dao.PersistentDiskDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs flavor list operations (such as persisting the flavor list in ZooKeeper).
 */
@Singleton
public class FlavorSqlBackend implements FlavorBackend {

  private static final Logger logger = LoggerFactory.getLogger(FlavorSqlBackend.class);

  private final FlavorDao flavorDao;
  private final VmDao vmDao;
  private final PersistentDiskDao persistentDiskDao;
  private final EphemeralDiskDao ephemeralDiskDao;
  private final TaskBackend taskBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public FlavorSqlBackend(FlavorDao flavorDao, VmDao vmDao, PersistentDiskDao persistentDiskDao,
                          EphemeralDiskDao ephemeralDiskDao, TaskBackend taskBackend,
                          TombstoneBackend tombstoneBackend) {
    this.flavorDao = flavorDao;
    this.vmDao = vmDao;
    this.persistentDiskDao = persistentDiskDao;
    this.ephemeralDiskDao = ephemeralDiskDao;
    this.taskBackend = taskBackend;
    this.tombstoneBackend = tombstoneBackend;
  }

  @Transactional
  public TaskEntity createFlavor(FlavorCreateSpec flavor) throws ExternalException {
    FlavorEntity flavorEntity = create(flavor);
    return taskBackend.createCompletedTask(flavorEntity, Operation.CREATE_FLAVOR);
  }

  @Transactional
  public Flavor getApiRepresentation(String id) throws ExternalException {
    return findById(id).toApiRepresentation();
  }

  @Transactional
  public TaskEntity prepareFlavorDelete(String id) throws ExternalException {
    FlavorEntity flavorEntity = findById(id);
    if (!FlavorState.READY.equals(flavorEntity.getState())) {
      throw new InvalidFlavorStateException(
          String.format("Invalid operation to delete flavor %s in state %s",
              flavorEntity.getId(), flavorEntity.getState()));
    }

    String flavorKind = flavorEntity.getKind();
    boolean isFlavorInUse;

    switch (flavorKind) {
      case Vm.KIND:
        isFlavorInUse = !vmDao.listByFlavor(id).isEmpty();
        break;
      case PersistentDisk.KIND:
        isFlavorInUse = !persistentDiskDao.findByFlavor(id).isEmpty();
        break;
      case EphemeralDisk.KIND:
        isFlavorInUse = !ephemeralDiskDao.findByFlavor(id).isEmpty();
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + flavorKind);
    }

    if (isFlavorInUse) {
      flavorEntity.setState(FlavorState.PENDING_DELETE);
      flavorDao.update(flavorEntity);
    } else {
      tombstoneBackend.create(flavorKind, flavorEntity.getId());
      flavorDao.delete(flavorEntity);
    }
    return taskBackend.createCompletedTask(flavorEntity, Operation.DELETE_FLAVOR);
  }

  @Transactional
  public List<FlavorEntity> getAll() throws ExternalException {
    return flavorDao.listAll();
  }

  @Transactional
  public List<Flavor> filter(Optional<String> name, Optional<String> kind) throws ExternalException {
    List<FlavorEntity> entityList;

    if (name.isPresent() && kind.isPresent()) {
      Optional<FlavorEntity> entity = flavorDao.findByNameAndKind(name.get(), kind.get());
      if (entity.isPresent()) {
        entityList = ImmutableList.of(entity.get());
      } else {
        entityList = ImmutableList.of();
      }
    } else if (name.isPresent()) {
      entityList = flavorDao.listByName(name.get());
    } else if (kind.isPresent()) {
      entityList = flavorDao.findByKind(kind.get());
    } else {
      entityList = flavorDao.listAll();
    }
    List<Flavor> resourceList = new ArrayList<>();

    for (FlavorEntity entity : entityList) {
      resourceList.add(entity.toApiRepresentation());
    }

    return resourceList;
  }

  @Transactional
  public FlavorEntity getEntityByNameAndKind(String name, String kind) throws ExternalException {
    checkNotNull(kind);
    checkNotNull(name);

    Optional<FlavorEntity> flavorEntity = flavorDao.findByNameAndKind(name, kind);
    if (!flavorEntity.isPresent()) {
      logger.info("Flavor with name:{} and kind: {} not found.", name, kind);
      throw new FlavorNotFoundException(kind, name);
    }
    return flavorEntity.get();
  }

  @Transactional
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    FlavorEntity flavorEntity = findById(id);
    return taskBackend.filter(flavorEntity.getId(), flavorEntity.getKind(), state);
  }

  @Transactional
  public FlavorEntity getEntityById(String id) throws ExternalException {
    checkNotNull(id);

    Optional<FlavorEntity> flavorEntityOptional = flavorDao.findById(id);

    if (!flavorEntityOptional.isPresent()) {
      throw new FlavorNotFoundException(id);
    }

    return flavorEntityOptional.get();
  }

  @Transactional
  public void tombstone(FlavorEntity flavor) throws ExternalException {
    List<VmEntity> vmsInUse = vmDao.listByFlavor(flavor.getId());
    if (!vmsInUse.isEmpty()) {
      logger.info("vm(s) {} are using flavor {}, mark flavor as PENDING_DELETE", vmsInUse, flavor);
      updateState(flavor, FlavorState.PENDING_DELETE);
      return;
    }

    tombstoneBackend.create(ImageEntity.KIND, flavor.getId());
    flavorDao.delete(flavor);
  }

  @Transactional
  public void updateState(FlavorEntity flavorEntity, FlavorState state) throws ExternalException {
    flavorEntity.setState(state);
    flavorDao.update(flavorEntity);
  }

  private FlavorEntity create(FlavorCreateSpec spec) throws ExternalException {
    if (flavorDao.findByNameAndKind(spec.getName(), spec.getKind()).isPresent()) {
      throw new NameTakenException(spec.getKind(), spec.getName());
    }

    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setName(spec.getName());

    switch (spec.getKind()) {
      case "ephemeral":
      case "ephemeral-disk":
        flavorEntity.setKind(EphemeralDisk.KIND);
        break;
      case "persistent":
      case "persistent-disk":
        flavorEntity.setKind(PersistentDisk.KIND);
        break;
      case "vm":
        flavorEntity.setKind(Vm.KIND);
        break;
      default:
        throw new IllegalArgumentException(String.format("Flavor kind %s is not allowed.", spec.getKind()));
    }

    List<QuotaLineItemEntity> costEntity = new ArrayList<>();
    for (QuotaLineItem quota : spec.getCost()) {
      QuotaLineItemEntity quotaEntity = new QuotaLineItemEntity();
      quotaEntity.setKey(quota.getKey());
      quotaEntity.setValue(quota.getValue());
      quotaEntity.setUnit(quota.getUnit());
      costEntity.add(quotaEntity);
    }
    flavorEntity.setCost(costEntity);

    flavorEntity.setState(FlavorState.READY);
    flavorDao.create(flavorEntity);
    return flavorEntity;
  }

  // findById method should be nested inside other @Transactional method
  private FlavorEntity findById(String id) throws FlavorNotFoundException {
    Optional<FlavorEntity> flavor = flavorDao.findById(id);

    if (flavor.isPresent()) {
      return flavor.get();
    }

    throw new FlavorNotFoundException(id);
  }
}
