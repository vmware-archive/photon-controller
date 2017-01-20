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
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with Service API.
 */
public interface ServiceApi {
  String getBasePath();

  Service getService(String serviceId) throws IOException;

  void getServiceAsync(String serviceId, FutureCallback<Service> responseCallback)
      throws IOException;

  Task delete(String serviceId) throws IOException;

  void deleteAsync(String serviceId, FutureCallback<Task> responseCallback)
      throws IOException;

  Task resize(String serviceId, int size) throws IOException;

  void resizeAsync(
      String serviceId, int size, FutureCallback<Task> responseCallback)
      throws IOException;

  ResourceList<Vm> getVmsInService(String serviceId) throws IOException;

  void getVmsInServiceAsync(String serviceId, FutureCallback<ResourceList<Vm>> responseCallback)
    throws IOException;
}
