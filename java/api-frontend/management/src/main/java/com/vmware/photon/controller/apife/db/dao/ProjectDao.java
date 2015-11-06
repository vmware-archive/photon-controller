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

import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the project Data Access Object class. It's responsible
 * for implementing the various DB access methods including create, and the queries by id, name, tag.
 */
public class ProjectDao extends BaseDao<ProjectEntity, TenantEntity> {
  @Inject
  public ProjectDao(SessionFactory sessionFactory) {
    super(sessionFactory, "tenant");
  }

  public List<ProjectEntity> findAll(String parentId) {
    return list(namedQuery(findAll).setString("tenantId", parentId));
  }

  public Optional<ProjectEntity> findByName(String name, String parentId) {
    ProjectEntity result = uniqueResult(namedQuery(findByName)
        .setString("name", name)
        .setString("tenantId", parentId));
    return Optional.fromNullable(result);
  }

  public List<ProjectEntity> listByName(String name, String parentId) {
    return list(namedQuery(findByName)
        .setString("name", name)
        .setString("tenantId", parentId));
  }

  public List<ProjectEntity> findByTag(String value, String parentId) {
    return list(namedQuery(findByTag)
        .setString("value", value)
        .setString("tenantId", parentId));
  }
}
