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

package com.vmware.photon.controller.housekeeper.xenon.mock.hostclient;

import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.StartImageOperationResultCode;
import com.vmware.photon.controller.host.gen.StartImageSweepResponse;
import com.vmware.photon.controller.housekeeper.xenon.mock.HostClientMock;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import java.util.List;

/**
 * Host client mock used to generate error conditions for the startImageSweep method.
 */
public class ErrorMockStartImageSweep extends HostClientMock {
  private StartImageOperationResultCode errorCode;

  public ErrorMockStartImageSweep() {
    this(null);
  }

  public ErrorMockStartImageSweep(StartImageOperationResultCode code) {
    this.errorCode = code;
  }

  @Override
  public void startImageSweep(String datastore, List<InactiveImageDescriptor> images, Long sweepRate, Long timeout,
                              AsyncMethodCallback<Host.AsyncClient.start_image_sweep_call> callback) {
    if (null == this.errorCode) {
      callback.onError(new TException("startImageScan error"));
      return;
    }

    callback.onComplete(
        MethodCallBuilder.buildStartImageSweepMethodCall(new StartImageSweepResponse(this.errorCode)));
  }
}
