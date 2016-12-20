/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.frontend.entities;

import java.util.Objects;

/**
 * Represents a datastore mount to a host.
 */
public class HostDatastoreEntity {
  private String datastoreId;
  private String mountPoint;
  private boolean imageDatastore;

  public HostDatastoreEntity() {
  }

  public String getDatastoreId() {
    return this.datastoreId;
  }

  public void setDatastoreId(String datastoreId) {
    this.datastoreId = datastoreId;
  }

  public String getMountPoint() {
    return mountPoint;
  }

  public void setMountPoint(String mountPoint) {
    this.mountPoint = mountPoint;
  }

  public boolean isImageDatastore() {
    return imageDatastore;
  }

  public void setImageDatastore(boolean imageDatastore) {
    this.imageDatastore = imageDatastore;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HostDatastoreEntity other = (HostDatastoreEntity) o;

    return Objects.equals(this.getDatastoreId(), other.getDatastoreId())
        && Objects.equals(this.getMountPoint(), other.getMountPoint())
        && this.isImageDatastore() == other.isImageDatastore();
  }
}
