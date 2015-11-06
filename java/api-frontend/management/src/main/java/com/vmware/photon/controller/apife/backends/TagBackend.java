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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TagBackend is performing Tag operations such as add tag etc.
 */
@Singleton
public class TagBackend {

  private static final Logger logger = LoggerFactory.getLogger(TagBackend.class);

  private final TagDao tagDao;

  private final TaskBackend taskBackend;

  @Inject
  public TagBackend(TagDao tagDao, TaskBackend taskBackend) {
    this.tagDao = tagDao;
    this.taskBackend = taskBackend;
  }

  @Transactional
  public TaskEntity addTag(InfrastructureEntity entity, Tag tag) throws ExternalException {
    TagEntity tagEntity = tagDao.findOrCreate(tag.getValue());
    entity.getTags().add(tagEntity);
    return taskBackend.createCompletedTask(entity, Operation.ADD_TAG);
  }
}
