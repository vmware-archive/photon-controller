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

import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.db.dao.LocalityDao;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * LocalityBackend is performing Locality operations.
 */
@Singleton
public class LocalityBackend {

  private final LocalityDao localityDao;

  @Inject
  public LocalityBackend(LocalityDao localityDao) {
    this.localityDao = localityDao;
  }

  /**
   * Create a list of LocalityEntities based on LocalitySpec specified during Vm creation.
   *
   * @param entity VmEntity or PersistentEntity
   * @param specs  List of LocalitySpec indicating affinities
   * @return List<LocalityEntity>
   * @throws com.vmware.photon.controller.api.common.exceptions.external.ExternalException
   */
  @Transactional
  public List<LocalityEntity> create(BaseEntity entity, List<LocalitySpec> specs) {
    List<LocalityEntity> affinities = new ArrayList<>();
    for (LocalitySpec spec : specs) {
      affinities.add(create(entity, spec));
    }
    return affinities;
  }

  /**
   * Create a LocalityEntity based on LocalitySpec specified during Vm creation.
   */
  @Transactional
  public LocalityEntity create(BaseEntity entity, LocalitySpec spec) {

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId(spec.getId());
    localityEntity.setKind(spec.getKind());
    if (entity instanceof VmEntity) {
      localityEntity.setVm((VmEntity) entity);
    } else if (entity instanceof PersistentDiskEntity) {
      localityEntity.setDisk((PersistentDiskEntity) entity);
    } else {
      throw new IllegalArgumentException();
    }

    return localityDao.create(localityEntity);
  }

}
