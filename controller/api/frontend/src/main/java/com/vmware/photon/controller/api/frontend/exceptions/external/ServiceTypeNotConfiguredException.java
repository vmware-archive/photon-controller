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

package com.vmware.photon.controller.api.frontend.exceptions.external;

import com.vmware.photon.controller.api.model.ServiceType;

/**
 * Exception thrown when a certain type service is not configured.
 */
public class ServiceTypeNotConfiguredException extends ExternalException {
  private static final long serialVersionUID = 1L;

  private ServiceType serviceType;

  public ServiceTypeNotConfiguredException(ServiceType serviceType) {
    super(ErrorCode.SERVICE_TYPE_NOT_CONFIGURED);
    this.serviceType = serviceType;
  }

  @Override
  public String getMessage() {
    if (serviceType != null) {
      return this.serviceType.toString() + " service is not configured yet.";
    } else {
      return "A valid and non-null service type must be provided";
    }
  }
}
