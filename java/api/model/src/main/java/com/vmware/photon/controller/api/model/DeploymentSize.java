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
 * Size information of a deployment.
 */
@ApiModel(value = "The class describes the size of the deployment.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentSize {

  @JsonProperty
  @ApiModelProperty(value = "Number of hosts in the deployment")
  private int numberHosts;

  @JsonProperty
  @ApiModelProperty(value = "Number of VMs in the deployment")
  private int numberVMs;

  @JsonProperty
  @ApiModelProperty(value = "Number of tenants in the deployment")
  private int numberTenants;

  @JsonProperty
  @ApiModelProperty(value = "Number of projects in the deployment")
  private int numberProjects;

  @JsonProperty
  @ApiModelProperty(value = "Number of datastores in the deployment")
  private int numberDatastores;

  @JsonProperty
  @ApiModelProperty(value = "Number of clusters in the deployment")
  private int numberClusters;

  public int getNumberHosts() {
    return this.numberHosts;
  }

  public void setNumberHosts(int numberHosts) {
    this.numberHosts = numberHosts;
  }

  public int getNumberVMs() {
    return this.numberVMs;
  }

  public void setNumberVMs(int numberVMs) {
    this.numberVMs = numberVMs;
  }

  public int getNumberTenants() {
    return this.numberTenants;
  }

  public void setNumberTenants(int numberTenants) {
    this.numberTenants = numberTenants;
  }

  public int getNumberProjects() {
    return this.numberProjects;
  }

  public void setNumberProjects(int numberProjects) {
    this.numberProjects = numberProjects;
  }

  public int getNumberDatastores() {
    return this.numberDatastores;
  }

  public void setNumberDatastores(int numberDatastores) {
    this.numberDatastores = numberDatastores;
  }

  public int getNumberClusters() {
    return this.numberClusters;
  }

  public void setNumberClusters(int numberClusters) {
    this.numberClusters = numberClusters;
  }
}
