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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import static com.google.common.base.Objects.toStringHelper;

import java.util.Objects;

/**
 * Image setting information returned from API.
 */
@ApiModel(value = "This class is the full fidelity representation of an ImageSetting object.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageSetting {

  @JsonProperty
  @ApiModelProperty(value = "Name of the setting", required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Default value of the setting", required = true)
  private String defaultValue;

  public ImageSetting() {
  }

  public ImageSetting(String name, String defaultValue) {
    this.name = name;
    this.defaultValue = defaultValue;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ImageSetting)) {
      return false;
    }

    ImageSetting that = (ImageSetting) o;

    return Objects.equals(name, that.name)
        && Objects.equals(defaultValue, that.defaultValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        name,
        defaultValue);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("name", name)
        .add("defaultValue", defaultValue)
        .toString();
  }
}
