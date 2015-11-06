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

import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;

import com.google.common.base.Optional;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * Base Disk DAO.
 *
 * @param <T> disk entity type
 */
public abstract class BaseDiskDao<T extends BaseDiskEntity> extends BaseDao<T, ProjectEntity> {

  public BaseDiskDao(SessionFactory sessionFactory) {
    super(sessionFactory, "project");
  }

  public List<T> findAll(ProjectEntity parent) {
    return list(namedQuery(findAll).setString("projectId", parent.getId()));
  }

  public Optional<T> findByName(String name, ProjectEntity parent) {
    T result = uniqueResult(namedQuery(findByName)
        .setString("name", name)
        .setString("projectId", parent.getId()));
    return Optional.fromNullable(result);
  }

  public List<T> listByName(String name, ProjectEntity parent) {
    return list(namedQuery(findByName)
        .setString("name", name)
        .setString("projectId", parent.getId()));
  }

  public List<T> findByTag(String value, ProjectEntity parent) {
    return list(namedQuery(findByTag)
        .setString("value", value)
        .setString("projectId", parent.getId()));
  }

  public List<T> findByFlavor(String flavorId) {
    return list(namedQuery(getEntityClassName() + ".findByFlavor")
        .setString("flavorId", flavorId));
  }
}
