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

/**
 * Gets thrown when resource ticket is not found.
 */
public class ResourceTicketNameNotFoundException extends ExternalException {
  private String tenantName;
  private String ticketName;

  public ResourceTicketNameNotFoundException(String tenantName, String ticketName) {
    super(ErrorCode.RESOURCE_TICKET_NOT_FOUND);
    this.tenantName = tenantName;
    this.ticketName = ticketName;

    addData("tenantName", tenantName);
    addData("ticketName", ticketName);
  }


  @Override
  public String getMessage() {
    return String.format("Resource ticket %s from tenant %s not found", ticketName, tenantName);
  }
}
