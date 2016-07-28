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

package com.vmware.photon.controller.api.frontend.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;

/**
 * Pagination configuration.
 */
public class PaginationConfig {
  public static final int DEFAULT_DEFAULT_PAGE_SIZE = 100;
  public static final int DEFAULT_MAX_PAGE_SIZE = 100;

  @Min(1)
  @JsonProperty("default_page_size")
  private int defaultPageSize = DEFAULT_DEFAULT_PAGE_SIZE;

  @JsonProperty("max_page_size")
  private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;

  @AssertTrue(message = "maxPageSize should be equal or larger than defaultPageSize")
  private boolean isValid() {
    return maxPageSize >= defaultPageSize;
  }

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  public void setDefaultPageSize(int defaultPageSize) {
    this.defaultPageSize = defaultPageSize;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }
}
