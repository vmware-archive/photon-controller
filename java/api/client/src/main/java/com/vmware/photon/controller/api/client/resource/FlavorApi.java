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

import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for interacting with Flavor API.
 */
public interface FlavorApi {
  String getBasePath();

  Task create(FlavorCreateSpec flavorCreateSpec) throws IOException;

  void createAsync(FlavorCreateSpec flavorCreateSpec, FutureCallback<Task> responseCallback)
      throws IOException;

  ResourceList<Flavor> listAll() throws IOException;

  ResourceList<Flavor> listAll(Map<String, String> queryParams) throws IOException;

  void listAllAsync(FutureCallback<ResourceList<Flavor>> responseCallback) throws IOException;

  Flavor getFlavor(String flavorId) throws IOException;

  void getFlavorAsync(String flavorId, FutureCallback<Flavor> responseCallback) throws IOException;

  Task delete(String id) throws IOException;

  void deleteAsync(String flavorId, FutureCallback<Task> responseCallback) throws IOException;
}
