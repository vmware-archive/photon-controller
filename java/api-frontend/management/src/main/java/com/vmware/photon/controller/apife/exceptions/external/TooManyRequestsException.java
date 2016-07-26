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
 * Gets thrown when resource creation cannot proceed because there was not enough quota allocated to accommodate it.
 */
public class TooManyRequestsException extends ExternalException {

  public TooManyRequestsException() {
    super(ErrorCode.TOO_MANY_REQUESTS);
  }

  @Override
  public String getMessage() {
    return "The API client is generating too many requests at the moment";
  }
}
