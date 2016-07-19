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

package com.vmware.photon.controller.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Objects;

/**
 * Represents datastore information as part of a host object.
 */
@ApiModel(value = "This class represents a datastore mount point.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostDatastore {

  @JsonProperty
  @ApiModelProperty(value = "Unique id of the datastore.", required = true)
  private String datastoreId;

  @JsonProperty
  @ApiModelProperty(value = "Mount point of the datastore.", required = true)
  private String mountPoint;

  @JsonProperty
  @ApiModelProperty(value = "Flag indicating if the host treats this datastore as an image datastore.")
  private boolean imageDatastore;

  public HostDatastore() {
  }

  public HostDatastore(String id, String mountPoint, Boolean isImageDatastore) {
    this.datastoreId = id;
    this.mountPoint = mountPoint;
    this.imageDatastore = isImageDatastore;
  }

  public String getDatastoreId() {
    return this.datastoreId;
  }

  public void setDatastoreId(String datastoreId) {
    this.datastoreId = datastoreId;
  }

  public String getMountPoint() {
    return this.mountPoint;
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

    HostDatastore other = (HostDatastore) o;

    return Objects.equals(this.getDatastoreId(), other.getDatastoreId())
        && Objects.equals(this.getMountPoint(), other.getMountPoint())
        && Objects.equals(this.isImageDatastore(), other.isImageDatastore());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.datastoreId,
        this.mountPoint,
        this.imageDatastore);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(getClass())
        .add("Id", this.getDatastoreId())
        .add("MountPoint", this.getMountPoint())
        .add("IsImageDatastore", this.isImageDatastore())
        .toString();
  }
}
