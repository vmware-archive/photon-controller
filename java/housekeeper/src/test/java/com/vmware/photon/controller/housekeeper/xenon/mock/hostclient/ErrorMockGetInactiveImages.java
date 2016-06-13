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

import com.vmware.photon.controller.host.gen.GetInactiveImagesResponse;
import com.vmware.photon.controller.host.gen.GetMonitoredImagesResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.xenon.mock.HostClientMock;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

/**
 * Host client mock used to generate error conditions for the getInactiveImages method.
 */
public class ErrorMockGetInactiveImages extends HostClientMock {

  private GetMonitoredImagesResultCode errorCode;

  public ErrorMockGetInactiveImages() {
    this(null);
  }

  public ErrorMockGetInactiveImages(GetMonitoredImagesResultCode code) {
    this.errorCode = code;
  }

  @Override
  public void getInactiveImages(String dataStore,
                                AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call> callback) {
    if (null == this.errorCode) {
      callback.onError(new TException("getInactiveImages error"));
      return;
    }

    callback.onComplete(
        MethodCallBuilder.buildGetInactiveImagesMethodCall(new GetInactiveImagesResponse(this.errorCode)));
  }

}
