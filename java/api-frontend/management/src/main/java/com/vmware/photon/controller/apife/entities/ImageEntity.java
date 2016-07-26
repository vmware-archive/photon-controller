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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.api.model.base.Named;
import com.vmware.photon.controller.apife.entities.base.BaseEntity;

import com.google.common.base.Objects.ToStringHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Image Entity.
 */
public class ImageEntity extends BaseEntity implements Named {

  public static final String KIND = "image";

  private String name;

  private Long size;

  private Integer totalImageDatastore;

  private Integer totalDatastore;

  private Integer replicatedDatastore;

  private Integer replicatedImageDatastore;

  private List<ImageSettingsEntity> imageSettings = new ArrayList<>();

  private ImageState state;

  private ImageReplicationType replicationType = ImageReplicationType.EAGER;

  @Override
  public String getKind() {
    return KIND;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public ImageState getState() {
    return state;
  }

  public void setState(ImageState state) {
    EntityStateValidator.validateStateChange(this.getState(), state, ImageState.PRECONDITION_STATES);
    this.state = state;
  }

  public ImageReplicationType getReplicationType() {
    return replicationType;
  }

  public void setReplicationType(ImageReplicationType replicationType) {
    this.replicationType = replicationType;
  }

  public List<ImageSettingsEntity> getImageSettings() {
    return imageSettings;
  }

  public void setImageSettings(List<ImageSettingsEntity> imageSettings) {
    this.imageSettings = imageSettings;
  }

  public Map<String, String> getImageSettingsMap() {
    Map<String, String> imageSettings = new HashMap<>();
    for (ImageSettingsEntity imageSetting : getImageSettings()) {
      imageSettings.put(imageSetting.getName(), imageSetting.getDefaultValue());
    }

    return imageSettings;
  }

  public Integer getReplicatedDatastore() {
    return replicatedDatastore;
  }

  public void setReplicatedDatastore(Integer replicatedDatastore) {
    this.replicatedDatastore = replicatedDatastore;
  }

  public Integer getReplicatedImageDatastore() {
    return replicatedImageDatastore;
  }

  public void setReplicatedImageDatastore(Integer replicatedImageDatastore) {
    this.replicatedImageDatastore = replicatedImageDatastore;
  }

  public Integer getTotalDatastore() {
    return totalDatastore;
  }

  public void setTotalDatastore(Integer totalDatastore) {
    this.totalDatastore = totalDatastore;
  }

  public Integer getTotalImageDatastore() {
    return totalImageDatastore;
  }

  public void setTotalImageDatastore(Integer totalImageDatastore) {
    this.totalImageDatastore = totalImageDatastore;
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o) == false) {
      return false;
    }

    ImageEntity other = (ImageEntity) o;

    return Objects.equals(this.getName(), other.getName()) &&
        Objects.equals(this.getState(), other.getState()) &&
        Objects.equals(this.getSize(), other.getSize());
  }

  protected ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("name", name)
        .add("state", state)
        .add("size", size)
        .add("totalDatastore", totalDatastore)
        .add("totalImageDatastore", totalImageDatastore)
        .add("replicatedDatastore", replicatedDatastore)
        .add("replicatedImageDatastore", replicatedImageDatastore);
  }

}
