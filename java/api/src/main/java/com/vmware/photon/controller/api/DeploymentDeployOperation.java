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

package com.vmware.photon.controller.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * Configuration parameters for the POST to /deployments/{id}/deploy.
 */
@ApiModel(value = "A class used to specify params for the 'deploy' operation.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentDeployOperation {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired state of the system at the end of deployment",
      allowableValues = "READY,PAUSED,BACKGROUND_PAUSED")
  private DeploymentState desiredState;

  public DeploymentDeployOperation() {
    desiredState = DeploymentState.PAUSED;
  }

  public DeploymentState getDesiredState() {
    return this.desiredState;
  }

  public void setDesiredState(DeploymentState state) {
    this.desiredState = state;
  }
}
