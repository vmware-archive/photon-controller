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

package com.vmware.photon.controller.swagger.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SwaggerResourceListings {
  @JsonProperty
  private String apiVersion;

  @JsonProperty
  private String swaggerVersion;

  @JsonProperty
  private String basePath;

  @JsonProperty
  private List<SwaggerResourceListingPath> apis;

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public String getSwaggerVersion() {
    return swaggerVersion;
  }

  public void setSwaggerVersion(String swaggerVersion) {
    this.swaggerVersion = swaggerVersion;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public List<SwaggerResourceListingPath> getApis() {
    return apis;
  }

  public void setApis(List<SwaggerResourceListingPath> apis) {
    this.apis = apis;
  }
}
