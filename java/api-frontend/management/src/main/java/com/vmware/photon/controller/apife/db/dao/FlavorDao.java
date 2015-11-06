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

import com.vmware.photon.controller.apife.entities.FlavorEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the flavor Data Access Object class. It's responsible.
 * for implementing the various DB access methods including create, and the queries by id, name, kind.
 */
public class FlavorDao extends ExtendedAbstractDao<FlavorEntity> {

  @Inject
  public FlavorDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  /**
   * This method finds the flavor by name and kind.
   *
   * @param name - supplies the name of the flavor
   * @param kind - supplies the kind of the flavor
   * @return - returns and Optional that includes a flavor or .absent() if no flavor by this name and kind
   * exists.
   */
  public Optional<FlavorEntity> findByNameAndKind(String name, String kind) {
    return Optional.fromNullable(uniqueResult(namedQuery("Flavor.findByNameAndKind")
        .setString("name", name)
        .setString("kind", kind)));
  }

  /**
   * This method finds the flavors by kind. Will return a list of flavors with the same kind, and different name
   *
   * @param kind - supplies the kind of the flavor
   * @return - the List<> of flavors
   * exists.
   */
  public List<FlavorEntity> findByKind(String kind) {
    return list(namedQuery("Flavor.findByKind").setString("kind", kind));
  }

}
