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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * General information.
 */
@ApiModel(value = "Information about Photon Controller")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {

  @JsonProperty
  @ApiModelProperty(value = "The base version of Photon Controller. Example: '1.1.0'")
  private String baseVersion;

  @JsonProperty
  @ApiModelProperty(value = "The full version of Photon Controller, which is the base version plus any "
      + "other identifiying information. Currently it includes the git commit hash. Example: '1.1.0-38a6409'")
  private String fullVersion;

  @JsonProperty
  @ApiModelProperty(value = "The git commit hash for this build. Example: '38a6409'")
  private String gitCommitHash;

  @JsonProperty
  @ApiModelProperty(value = "Type of networking in use, either PHYSICAL or SOFTWARE_BASED", required = true)
  private NetworkType networkType;

  public String getBaseVersion() {
    return this.baseVersion;
  }

  public void setBaseVersion(String baseVersion) {
    this.baseVersion = baseVersion;
  }

  public String getFullVersion() {
    return this.fullVersion;
  }

  public void setFullVersion(String fullVersion) {
    this.fullVersion = fullVersion;
  }

  public String getGitCommitHash() {
    return this.gitCommitHash;
  }

  public void setGitCommitHash(String gitCommitHash) {
    this.gitCommitHash = gitCommitHash;
  }

  public NetworkType getNetworkType() {
    return this.networkType;
  }

  public void setNetworkType(NetworkType networkType) {
    this.networkType = networkType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Info)) {
      return false;
    }

    Info info = (Info) o;

    return this.networkType == info.networkType
        && Objects.equal(this.baseVersion, info.baseVersion)
        && Objects.equal(this.fullVersion, info.fullVersion)
        && Objects.equal(this.gitCommitHash, info.gitCommitHash);
  }

  @Override
  public int hashCode() {
    return networkType.hashCode();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("networkType", networkType)
        .toString();
  }
}
