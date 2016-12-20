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

import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.frontend.clients.VmFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.api.model.VmMetadata;

import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * This class implements VM API for communicating with APIFE locally.
 */
public class VmLocalApi implements VmApi {
  private final VmFeClient vmFeClient;
  private final ExecutorService executorService;
  private static final Logger logger = LoggerFactory.getLogger(VmLocalApi.class);

  public VmLocalApi(VmFeClient vmFeClient, ExecutorService executorService) {
    this.vmFeClient = vmFeClient;
    this.executorService = executorService;
  }

  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Vm getVm(String vmId) throws IOException {
    return null;
  }

  @Override
  public void getVmAsync(String vmId, FutureCallback<Vm> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Task> getTasksForVm(String vmId) throws IOException {
    return null;
  }

  @Override
  public void getTasksForVmAsync(String vmId, FutureCallback<ResourceList<Task>> responseCallback) throws IOException {

  }

  @Override
  public Task delete(String id) throws IOException {
    try {
      return vmFeClient.delete(id);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException {
    executorService.submit(() -> {
      try {
        Task task = delete(id);
        responseCallback.onSuccess(task);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }

  @Override
  public Task addTagToVm(String vmId, Tag tag) throws IOException {
    return null;
  }

  @Override
  public void addTagToVmAsync(String vmId, Tag tag, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task performStartOperation(String vmId) throws IOException {
    try {
      return vmFeClient.operate(vmId, Operation.START_VM);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void performStartOperationAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {
    executorService.submit(() -> {
      try {
        Task task = performStartOperation(vmId);
        responseCallback.onSuccess(task);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }

  @Override
  public Task performStopOperation(String vmId) throws IOException {
    try {
      return vmFeClient.operate(vmId, Operation.STOP_VM);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void performStopOperationAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {
    executorService.submit(() -> {
      try {
        Task task = performStopOperation(vmId);
        responseCallback.onSuccess(task);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }

  @Override
  public Task performRestartOperation(String vmId) throws IOException {
    return null;
  }

  @Override
  public void performRestartOperationAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task performResumeOperation(String vmId) throws IOException {
    return null;
  }

  @Override
  public void performResumeOperationAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task performSuspendOperation(String vmId) throws IOException {
    return null;
  }

  @Override
  public void performSuspendOperationAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task attachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException {
    return null;
  }

  @Override
  public void attachDiskAsync(String vmId, VmDiskOperation vmDiskOperation, FutureCallback<Task> responseCallback)
      throws IOException {

  }

  @Override
  public Task detachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException {
    return null;
  }

  @Override
  public void detachDiskAsync(String vmId, VmDiskOperation vmDiskOperation, FutureCallback<Task> responseCallback)
      throws IOException {

  }

  @Override
  public Task uploadAndAttachIso(String vmId, String inputFileName) throws IOException {
    try {
      return vmFeClient.attachIso(vmId, new FileInputStream(inputFileName), inputFileName);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Task detachIso(String vmId) throws IOException {
    return null;
  }

  @Override
  public void detachIsoAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task getNetworks(String vmId) throws IOException {
    try {
      return vmFeClient.getNetworks(vmId);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void getNetworksAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {
    executorService.submit(() -> {
      try {
        Task task = getNetworks(vmId);
        responseCallback.onSuccess(task);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }

  @Override
  public Task setMetadata(String vmId, VmMetadata metadata) throws IOException {
    return null;
  }

  @Override
  public void setMetadataAsync(String vmId, VmMetadata metadata, FutureCallback<Task> responseCallback)
      throws IOException {

  }
}
