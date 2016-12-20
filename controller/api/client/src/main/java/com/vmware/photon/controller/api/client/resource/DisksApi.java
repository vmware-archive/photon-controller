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

import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with Disks API.
 */
public interface DisksApi {
  String getBasePath();

  PersistentDisk getDisk(String diskId) throws IOException;

  void getDiskAsync(String diskId, FutureCallback<PersistentDisk> responseCallback) throws
      IOException;

  ResourceList<Task> getTasksForDisk(String diskId) throws IOException;

  void getTasksForDiskAsync(String diskId, FutureCallback<ResourceList<Task>> responseCallback)
      throws IOException;

  Task delete(String diskId) throws IOException;

  void deleteAsync(String diskId, FutureCallback<Task> responseCallback) throws
      IOException;
}
