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
package com.vmware.photon.controller.api.client.resource;

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with ResourceTicket API.
 */
public interface ResourceTicketApi {
  String getBasePath();

  ResourceTicket getResourceTicket(String resourceTicketId) throws IOException;

  void getResourceTicketAsync(String resourceTicketId, FutureCallback<ResourceTicket>
      responseCallback) throws
      IOException;

  ResourceList<Task> getTasksForResourceTicket(String resourceTicketId) throws IOException;

  void getTasksForResourceTicketAsync(String resourceTicketId, FutureCallback<ResourceList<Task>>
      responseCallback) throws
      IOException;
}
