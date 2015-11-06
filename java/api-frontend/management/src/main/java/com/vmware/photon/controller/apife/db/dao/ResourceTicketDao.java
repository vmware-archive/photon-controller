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

import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.inject.Inject;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * This class implements the tenant Data Access Object class. It's responsible
 * for implementing the various DB access methods including create, and the queries by id, name, tag.
 */
public class ResourceTicketDao extends BaseDao<ResourceTicketEntity, TenantEntity> {

  @Inject
  public ResourceTicketDao(SessionFactory sessionFactory) {
    super(sessionFactory, "tenant");
  }

  public List<ResourceTicketEntity> findByParent(String parentId) {
    return list(namedQuery("ResourceTicket.findByParent")
        .setString("parentId", parentId));
  }
}
