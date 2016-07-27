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

package com.vmware.photon.controller.apife.exceptions.external;

/**
 * Thrown when the page size is invalid.
 */
public class InvalidPageSizeException extends ExternalException {

  private final int pageSize;
  private final int minPageSize;
  private final int maxPageSize;

  public InvalidPageSizeException(int pageSize, int minPageSize, int maxPageSize) {
    super(ErrorCode.INVALID_PAGE_SIZE);

    this.pageSize = pageSize;
    this.minPageSize = minPageSize;
    this.maxPageSize = maxPageSize;

    addData("pageSize", String.valueOf(pageSize));
    addData("minPageSize", String.valueOf(minPageSize));
    addData("maxPageSize", String.valueOf(maxPageSize));
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getMinPageSize() {
    return minPageSize;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  @Override
  public String getMessage() {
    return "The page size '" + pageSize + "' is not between '" + minPageSize + "' and '" + maxPageSize + "'";
  }
}
