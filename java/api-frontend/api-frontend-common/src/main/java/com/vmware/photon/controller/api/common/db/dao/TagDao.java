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

package com.vmware.photon.controller.api.common.db.dao;

import com.vmware.photon.controller.api.common.entities.base.TagEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * DAO layer for {@link TagEntity}.
 */
@Singleton
public class TagDao extends AbstractDAO<TagEntity> {

  @Inject
  public TagDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public TagEntity findOrCreate(String value) {
    List<TagEntity> tags = list(namedQuery("Tag.findByValue")
        .setString("value", value));
    if (tags.isEmpty()) {
      TagEntity tag = new TagEntity();
      tag.setValue(value);
      tags.add(persist(tag));
    }
    return tags.get(0);
  }
}
