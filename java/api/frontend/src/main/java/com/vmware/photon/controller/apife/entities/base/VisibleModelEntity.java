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

package com.vmware.photon.controller.apife.entities.base;

import com.vmware.photon.controller.api.model.base.Named;
import com.vmware.photon.controller.apife.entities.TaggableEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for model entities that can be exposed to user on their own
 * and thus should be named and taggable.
 */
public abstract class VisibleModelEntity extends ModelEntity implements Named, TaggableEntity {
  private String name;

  private Set<TagEntity> tags;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<TagEntity> getTags() {
    if (tags == null) {
      tags = new HashSet<>();
    }
    return tags;
  }

  public void setTags(Set<TagEntity> tags) {
    this.tags = tags;
  }
}
