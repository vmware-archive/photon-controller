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

import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.apife.config.MaintenanceConfig;
import com.vmware.photon.controller.apife.db.dao.TombstoneDao;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TombstoneBackend is responsible for writing the tombstone entries for an object.
 */
@Singleton
public class TombstoneSqlBackend implements TombstoneBackend {

  private static final Logger logger = LoggerFactory.getLogger(TombstoneSqlBackend.class);

  private final TombstoneDao tombstoneDao;

  private final MaintenanceConfig maintenanceConfig;

  @Inject
  public TombstoneSqlBackend(TombstoneDao tombstoneDao, MaintenanceConfig maintenanceConfig) {
    this.tombstoneDao = tombstoneDao;
    this.maintenanceConfig = maintenanceConfig;
  }

  @Transactional
  public TombstoneEntity create(String entityKind, String entityId) {
    TombstoneEntity tombstone = new TombstoneEntity();
    tombstone.setEntityKind(entityKind);
    tombstone.setEntityId(entityId);
    tombstone.setTombstoneTime(System.currentTimeMillis());
    tombstoneDao.create(tombstone);
    return tombstone;
  }

  @Transactional
  public List<TombstoneEntity> getStaleTombstones() {
    List<TombstoneEntity> tombstones = tombstoneDao.listByTimeOlderThan(
        System.currentTimeMillis() - this.maintenanceConfig.getTaskExpirationThreshold().toMilliseconds());

    logger.info("Found {} stale tombstones", tombstones.size());
    logger.debug("Stale tombstones are {}", tombstones);
    return tombstones;
  }

  @Transactional
  public void delete(TombstoneEntity tombstone) {
    logger.debug("Deleting tombstone {}", tombstone);
    tombstoneDao.delete(tombstone);
    logger.debug("Deleted tombstone {}", tombstone);
  }

  @Transactional
  public TombstoneEntity getByEntityId(String id) {
    return tombstoneDao.findByEntityId(id).orNull();
  }

}
