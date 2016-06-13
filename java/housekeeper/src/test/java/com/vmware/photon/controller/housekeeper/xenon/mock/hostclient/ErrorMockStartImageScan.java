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
import com.vmware.photon.controller.host.gen.StartImageScanResponse;
import com.vmware.photon.controller.housekeeper.xenon.mock.HostClientMock;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

/**
 * Host client mock used to generate error conditions for the startImageScan method.
 */
public class ErrorMockStartImageScan extends HostClientMock {

  private StartImageOperationResultCode errorCode;

  public ErrorMockStartImageScan() {
    this(null);
  }

  public ErrorMockStartImageScan(StartImageOperationResultCode code) {
    this.errorCode = code;
  }

  @Override
  public void startImageScan(String dataStore, Long scanRate, Long timeout,
                             AsyncMethodCallback<Host.AsyncClient.start_image_scan_call> callback) {
    if (null == this.errorCode) {
      callback.onError(new TException("startImageScan error"));
      return;
    }

    callback.onComplete(
        MethodCallBuilder.buildStartImageScanMethodCall(new StartImageScanResponse(this.errorCode)));
  }
}
