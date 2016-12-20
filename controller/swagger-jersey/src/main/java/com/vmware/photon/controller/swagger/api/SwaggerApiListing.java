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

public class SwaggerApiListing {
  @JsonProperty
  private String path;

  @JsonProperty
  private List<SwaggerOperation> operations;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public List<SwaggerOperation> getOperations() {
    return operations;
  }

  public void setOperations(List<SwaggerOperation> operations) {
    this.operations = operations;
  }
}
