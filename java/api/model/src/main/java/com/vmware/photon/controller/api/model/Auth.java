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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Objects;

/**
 * Contains Authentication/ Authorization information.
 */
@ApiModel(value = "Contains Authentication/ Authorization information")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Auth {

  @JsonProperty
  @ApiModelProperty(value = "Flag that indicates if auth is enabled or not", required = true)
  private boolean enabled = false;

  @JsonProperty
  @ApiModelProperty(value = "Url of the Authentication Server Endpoint", required = false)
  private String endpoint;

  @JsonProperty
  @ApiModelProperty(value = "The Authentication Server Port", required = false)
  private Integer port;

  public boolean getEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Auth other = (Auth) o;

    return Objects.equals(this.getEnabled(), other.getEnabled())
        && Objects.equals(this.getEndpoint(), other.getEndpoint())
        && Objects.equals(this.getPort(), other.getPort());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getEnabled(),
        this.getEndpoint(),
        this.getPort());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("enabled", this.getEnabled())
        .add("endpoint", this.getEndpoint())
        .add("port", this.getPort());
  }
}
