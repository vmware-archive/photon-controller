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
package com.vmware.photon.controller.api.frontend.clients.api;

import com.vmware.photon.controller.api.client.resource.ResourceTicketApi;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * This class implements ResourceTicket API for communicating with APIFE locally.
 */
public class ResourceTicketLocalApi implements ResourceTicketApi {
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public ResourceTicket getResourceTicket(String resourceTicketId) throws IOException {
    return null;
  }

  @Override
  public void getResourceTicketAsync(String resourceTicketId, FutureCallback<ResourceTicket> responseCallback)
      throws IOException {

  }

  @Override
  public ResourceList<Task> getTasksForResourceTicket(String resourceTicketId) throws IOException {
    return null;
  }

  @Override
  public void getTasksForResourceTicketAsync(String resourceTicketId,
                                             FutureCallback<ResourceList<Task>> responseCallback) throws IOException {

  }
}
