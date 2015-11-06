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

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.apife.db.dao.PortGroupDao;
import com.vmware.photon.controller.apife.entities.PortGroupEntity;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * portgroup service backend.
 */
@Singleton
public class PortGroupSqlBackend implements PortGroupBackend {

  private static final Logger logger = LoggerFactory.getLogger(PortGroupSqlBackend.class);

  private final PortGroupDao portGroupDao;

  @Inject
  public PortGroupSqlBackend(PortGroupDao portGroupDao) {
    this.portGroupDao = portGroupDao;
  }

  @Override
  public List<PortGroup> filter(Optional<String> name, Optional<UsageTag> usageTag) {
    logger.error("PortGroupSqlBackend does not implement filter method");
    throw new RuntimeException("PortGroupSqlBackend does not implement filter method");
  }

  @Transactional
  public List<PortGroup> listAll() {
    List<PortGroup> resourceList = new ArrayList<>();
    List<PortGroupEntity> list = portGroupDao.listAll();

    for (PortGroupEntity entity : list) {
      resourceList.add(toApiRepresentation(entity));
    }

    return resourceList;
  }

  @Transactional
  public List<PortGroup> listByUsageTag(UsageTag usageTag) {
    List<PortGroup> resourceList = new ArrayList<>();
    List<PortGroupEntity> list = portGroupDao.listAllByUsage(usageTag);

    for (PortGroupEntity entity : list) {
      resourceList.add(toApiRepresentation(entity));
    }

    return resourceList;
  }

  @Override
  @Transactional
  public PortGroup toApiRepresentation(String id) throws PortGroupNotFoundException {
    PortGroupEntity portGroupEntity = findById(id);
    return toApiRepresentation(portGroupEntity);
  }

  private PortGroupEntity findById(String id) throws PortGroupNotFoundException {
    Optional<PortGroupEntity> portGroupEntity = portGroupDao.findById(id);

    if (portGroupEntity.isPresent()) {
      return portGroupEntity.get();
    } else {
      throw new PortGroupNotFoundException(id);
    }
  }

  private PortGroup toApiRepresentation(PortGroupEntity portGroupEntity) {
    PortGroup portGroup = new PortGroup();
    portGroup.setId(portGroupEntity.getId());
    portGroup.setName(portGroupEntity.getPortGroupName());
    portGroup.setUsageTags(UsageTagHelper.deserialize(portGroupEntity.getUsageTags()));

    return portGroup;
  }
}
