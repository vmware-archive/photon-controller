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

import com.vmware.photon.controller.api.model.base.Base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.Objects;

/**
 * ServiceConfiguration API representation.
 */
@ApiModel(value = "The model to describe service configuration.")
@JsonIgnoreProperties(ignoreUnknown =  true)
public class ServiceConfiguration extends Base {

  public static final String KIND = "serviceConfig";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"serviceConfig\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the service type.",
      allowableValues = ServiceType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private ServiceType type;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the ID of the service image.")
  @NotNull
  private String imageId;

  @JsonProperty
  @ApiModelProperty(value = "kind=\"" + KIND + "\"", required = true)
  @Override
  public String getKind() {
    return KIND;
  }

  public ServiceType getType() {
    return type;
  }

  public void setType(ServiceType type) {
    this.type = type;
  }

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    ServiceConfiguration other = (ServiceConfiguration) o;

    return Objects.equals(type, other.type) &&
        Objects.equals(imageId, other.imageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), type, imageId);
  }

  @Override
  public String toString() {
    return super.toStringHelper()
        .add("type", type)
        .add("imageId", imageId)
        .toString();
  }
}
