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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.base.VisibleModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Objects;

/**
 * Image Structure.
 */
@ApiModel(value = "This class represents a disk template, which can be used to generate a boot disk.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Image extends VisibleModel {

  public static final String KIND = "image";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"image\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Image",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  private ImageState state;

  @JsonProperty
  @ApiModelProperty(value = "Image size (in bytes)", required = true)
  private Long size;

  @JsonProperty
  @ApiModelProperty(value = "Image replication type", required = true)
  private ImageReplicationType replicationType;

  @JsonProperty
  @ApiModelProperty(value = "Settings for creating VM", required = true)
  private List<ImageSetting> settings;

  @JsonProperty
  @ApiModelProperty(value = "Ratio of datastores in the systems that have this image copy", required = true)
  private String replicationProgress;

  @JsonProperty
  @ApiModelProperty(value = "Ratio of image datastores in the systems that have this image copy", required = true)
  private String seedingProgress;

  @Override
  public String getKind() {
    return kind;
  }

  public ImageState getState() {
    return state;
  }

  public void setState(ImageState state) {
    this.state = state;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public ImageReplicationType getReplicationType() {
    return replicationType;
  }

  public void setReplicationType(ImageReplicationType replicationType) {
    this.replicationType = replicationType;
  }

  public List<ImageSetting> getSettings() {
    return settings;
  }

  public void setSettings(List<ImageSetting> settings) {
    this.settings = settings;
  }

  public String getReplicationProgress() {
    return replicationProgress;
  }

  public void setReplicationProgress(String replicationProgress) {
    this.replicationProgress = replicationProgress;
  }

  public String getSeedingProgress() {
    return seedingProgress;
  }

  public void setSeedingProgress(String seedingProgress) {
    this.seedingProgress = seedingProgress;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Image other = (Image) o;

    return super.equals(other) &&
        Objects.equals(this.getId(), other.getId()) &&
        Objects.equals(this.getName(), other.getName()) &&
        Objects.equals(this.getState(), other.getState()) &&
        Objects.equals(this.size, other.size) &&
        Objects.equals(this.replicationType, other.replicationType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(), getName(), getState(), getSize(), getReplicationType());
  }
}
