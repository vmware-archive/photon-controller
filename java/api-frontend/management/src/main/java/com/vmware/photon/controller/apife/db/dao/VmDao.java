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
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the VM Data Access Object class. It's responsible
 * for implementing the various DB access methods including create, and the queries by id, name, tag.
 * In addition to the VM primitives, this DAO is responsible for all DB access to the VmTag entities.
 */
public class VmDao extends BaseDao<VmEntity, ProjectEntity> {
  @Inject
  public VmDao(SessionFactory sessionFactory) {
    super(sessionFactory, "project");
  }

  public List<VmEntity> findAll(ProjectEntity parent) {
    return list(namedQuery(findAll).setString("projectId", parent.getId()));
  }

  public Optional<VmEntity> findByName(String name, ProjectEntity parent) {
    VmEntity result = uniqueResult(namedQuery(findByName)
        .setString("name", name)
        .setString("projectId", parent.getId()));
    return Optional.fromNullable(result);
  }

  public List<VmEntity> listByName(String name, ProjectEntity parent) {
    return list(namedQuery(findByName)
        .setString("name", name)
        .setString("projectId", parent.getId()));
  }

  public List<VmEntity> findByTag(String value, ProjectEntity parent) {
    return list(namedQuery(findByTag)
        .setString("value", value)
        .setString("projectId", parent.getId()));
  }

  public List<VmEntity> listByFlavor(String id) {
    return list(namedQuery(getEntityClassName() + ".findByFlavor")
        .setString("flavorId", id));
  }

  public List<VmEntity> listByImage(String imageId) {
    return list(namedQuery(getEntityClassName() + ".findByImage")
        .setString("imageId", imageId));
  }

  public List<VmEntity> listAllByHostIp(String hostIp) {
    return list(namedQuery(getEntityClassName() + ".listAllByHostIp")
        .setString("hostIp", hostIp));
  }

  public int countVmsByHostIp(String hostIp) {
    return ((Long) (namedQuery(getEntityClassName() + ".countVmsByHostIp")
        .setString("hostIp", hostIp)).uniqueResult()).intValue();
  }
}
