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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.Query;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * DAO for attached disk entities.
 */
public class AttachedDiskDao extends ExtendedAbstractDao<AttachedDiskEntity> {
  @Inject
  public AttachedDiskDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public Optional<AttachedDiskEntity> findByDisk(BaseDiskEntity disk) {
    Query query;
    switch (disk.getKind()) {
      case PersistentDisk.KIND:
        query = namedQuery("AttachedDisk.findByPersistentDisk");
        break;
      case EphemeralDisk.KIND:
        query = namedQuery("AttachedDisk.findByEphemeralDisk");
        break;
      default:
        throw new IllegalArgumentException(disk.getKind());
    }

    return Optional.fromNullable(uniqueResult(query.setString("diskId", disk.getId())));
  }

  public List<AttachedDiskEntity> findByVmId(String vmId) {
    return list(namedQuery("AttachedDisk.findByVmId")
        .setString("vmId", vmId));
  }
}
