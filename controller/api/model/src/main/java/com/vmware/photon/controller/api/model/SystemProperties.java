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

/**
 * System API representation.
 */
@ApiModel(value = "Updation and Retrival of System Properties.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemProperties {
  @JsonProperty
  @ApiModelProperty(value = "This is Quorum Setting for Photon Controller. "
      + "When there are multiple Photon Controller hosts, this indicates how many hosts must"
      + "accept a change before the user can get a response. Usually is it a majority(e.g. when "
      + "there are 3 hosts, this should be 2). In some special cases (like shutting down) it may"
      + "be appropriate to set this to be the same as the number of nodes. Please do not modify the "
      + "quorum unless you know exactly what you are doing. Incorrectly modifying this can damage your installation.",
      required = false)
  private String quorum;

  public String getQuorum() {
    return quorum;
  }
}
