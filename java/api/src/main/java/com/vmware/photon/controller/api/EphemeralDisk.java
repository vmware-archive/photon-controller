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

package com.vmware.photon.controller.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * Ephemeral disk API representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EphemeralDisk extends BaseDisk {

  public static final String KIND = "ephemeral-disk";
  public static final String KIND_SHORT_FORM = "ephemeral";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"ephemeral-disk\"", required = true)
  private String kind = KIND;

  @Override
  public String getKind() {
    return kind;
  }
}
