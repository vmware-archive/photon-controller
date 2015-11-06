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

import com.vmware.photon.controller.apife.entities.TombstoneEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * DAO layer for {@link TombstoneEntity}.
 */
@Singleton
public class TombstoneDao extends ExtendedAbstractDao<TombstoneEntity> {

  @Inject
  public TombstoneDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  /**
   * This method is used for looking up all of the tombstones that are associated with a specific kind.
   *
   * @param kind - supplies the kind value to scope the search to
   * @return - returns a list of TombstoneEntity objects for the specific kind
   */
  public List<TombstoneEntity> findByEntityKind(String kind) {
    return list(namedQuery("Tombstone.findByEntityKind")
        .setString("entityKind", kind));
  }

  /**
   * This method is used for looking up the tombstone for the specified entityId.
   *
   * @return - returns a list of TombstoneEntity objects
   */
  // todo(markl): pagination, https://www.pivotaltracker.com/story/show/45719695
  public Optional<TombstoneEntity> findByEntityId(String id) {
    TombstoneEntity result = uniqueResult(namedQuery("Tombstone.findByEntityId")
        .setString("entityId", id));
    return Optional.fromNullable(result);
  }

  /**
   * Query for finding entries older than specified time.
   *
   * @param date
   * @return returns a list of TombstoneEntity objects
   */
  public List<TombstoneEntity> listByTimeOlderThan(long date) {
    return list(namedQuery("Tombstone.listByTimeOlderThan")
        .setLong("date", date));
  }
}
