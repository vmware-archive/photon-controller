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
import com.wordnik.swagger.annotations.ApiModel;

/**
 * Indicates that an APIFE host is running and available
 * Note that there is no data here: the /available API will
 * give an HTTP 200 response with an empty body to indicate
 * that we are available. It's intended for us by a front-end
 * load balancer, so it's very lightweight.
 */
@ApiModel(value = "Indicates that an API Frontend is running and available")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Available {

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    // We have no data--all objects are identical
    return true;
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this);
  }
}
