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

package com.vmware.photon.controller.chairman.service;

import com.vmware.photon.controller.resource.gen.DatastoreType;

import java.util.HashSet;
import java.util.Set;

/**
 * Datastore model.
 */
public class Datastore {

  private final String id;
  private final DatastoreType type;
  private final Set<String> tags;

  public Datastore(String id, DatastoreType type) {
    this(id, type, new HashSet<String>());
  }

  public Datastore(String id, DatastoreType type, Set<String> tags) {
    this.id = id;
    this.type = type;
    this.tags = tags;
  }

  public String getId() {
    return id;
  }

  public DatastoreType getType() {
    return type;
  }

  public Set<String> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object o) {
    return equals(o, false);
  }

  public boolean equals(Object o, boolean compareTags) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Datastore datastore = (Datastore) o;
    boolean equalTags = true;
    if (compareTags) {
      equalTags = tags.equals(datastore.getTags());
    }

    // Not comparing availability zone since we only care about the id and tags
    return id.equals(datastore.id) & equalTags;
  }

  @Override
  public int hashCode() {
    // Not using faultDomain since it's mutable.
    return id.hashCode();
  }

  @Override
  public String toString() {
    return String.format("Datastore{id=%s, type=%s}", id, type);
  }
}
