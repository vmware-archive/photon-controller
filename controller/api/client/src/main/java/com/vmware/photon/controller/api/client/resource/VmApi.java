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
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.api.model.VmMetadata;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with VM API.
 */
public interface VmApi {
  String getBasePath();

  Vm getVm(String vmId) throws IOException;

  void getVmAsync(String vmId, FutureCallback<Vm>
      responseCallback) throws IOException;

  ResourceList<Task> getTasksForVm(String vmId) throws IOException;

  void getTasksForVmAsync(String vmId,
                          FutureCallback<ResourceList<Task>> responseCallback)
      throws IOException;

  Task delete(String id) throws IOException;

  void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException;

  Task addTagToVm(String vmId, Tag tag) throws IOException;

  void addTagToVmAsync(String vmId, Tag tag, FutureCallback<Task> responseCallback)
      throws IOException;

  Task performStartOperation(String vmId) throws IOException;

  void performStartOperationAsync(String vmId,
                                  FutureCallback<Task> responseCallback)
      throws IOException;

  Task performStopOperation(String vmId) throws IOException;

  void performStopOperationAsync(String vmId,
                                 FutureCallback<Task> responseCallback)
      throws IOException;

  Task performRestartOperation(String vmId) throws IOException;

  void performRestartOperationAsync(String vmId,
                                    FutureCallback<Task> responseCallback)
      throws IOException;

  Task performResumeOperation(String vmId) throws IOException;

  void performResumeOperationAsync(String vmId,
                                   FutureCallback<Task> responseCallback)
      throws IOException;

  Task performSuspendOperation(String vmId) throws IOException;

  void performSuspendOperationAsync(String vmId,
                                    FutureCallback<Task> responseCallback)
      throws IOException;

  Task attachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException;

  void attachDiskAsync(String vmId, VmDiskOperation vmDiskOperation,
                       FutureCallback<Task> responseCallback)
      throws IOException;

  Task detachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException;

  void detachDiskAsync(String vmId, VmDiskOperation vmDiskOperation,
                       FutureCallback<Task> responseCallback)
      throws IOException;

  Task uploadAndAttachIso(String vmId, String inputFileName) throws IOException;

  Task detachIso(String vmId) throws IOException;

  void detachIsoAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException;

  Task getNetworks(String vmId) throws IOException;

  void getNetworksAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException;

  Task setMetadata(String vmId, VmMetadata metadata)
      throws IOException;

  void setMetadataAsync(
      String vmId,
      VmMetadata metadata,
      FutureCallback<Task> responseCallback)
          throws IOException;
}
