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

package com.vmware.photon.controller.api.frontend.entities.base;

import com.google.common.base.Objects.ToStringHelper;

import java.util.Objects;

/**
 * Base class for all DB entities.
 */
public abstract class BaseEntity implements Cloneable {

  private String id;

  public abstract String getKind();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BaseEntity other = (BaseEntity) o;
    return Objects.equals(this.getId(), other.getId())
        && Objects.equals(this.getKind(), other.getKind());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, getKind());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("id", id)
        .add("kind", getKind());
  }
}
