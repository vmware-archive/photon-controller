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

import com.vmware.photon.controller.api.model.Tag;

import java.util.Objects;

/**
 * Tag entity.
 */
public class TagEntity extends ModelEntity {

  private String value;

  @Override
  public String getKind() {
    return Tag.KIND;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TagEntity other = (TagEntity) o;
    return Objects.equals(this.value, other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
