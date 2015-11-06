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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResult;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResultCode;

import org.apache.thrift.TException;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mocked class for {@link HousekeeperClient}.
 */
public class HousekeeperClientMock extends HousekeeperClient {

  private RemoveImageResultCode removeImageResultCode;

  public HousekeeperClientMock(ClientProxy<Housekeeper.AsyncClient> proxy) {
    super(proxy, new HousekeeperClientConfig());
  }

  @Override
  public void triggerImageRemoval(
      String image,
      SyncHandler<RemoveImageResponse, Housekeeper.AsyncClient.remove_image_call> handler)
      throws InterruptedException, RpcException {
    checkNotNull(removeImageResultCode);
    Housekeeper.AsyncClient.remove_image_call removeImageCall =
        mock(Housekeeper.AsyncClient.remove_image_call.class);
    RemoveImageResponse response = new RemoveImageResponse(
        new RemoveImageResult(removeImageResultCode));
    if (RemoveImageResultCode.SYSTEM_ERROR.equals(removeImageResultCode)) {
      response.getResult().setError("SystemError");
    }
    try {
      when(removeImageCall.getResult()).thenReturn(response);
    } catch (TException e) {
      throw new RuntimeException(
          String.format("Failed to mock removeImageCall.getResult: %s", e.getMessage()));
    }
    handler.onComplete(removeImageCall);
  }

  public void setRemoveImageResultCode(RemoveImageResultCode removeImageResultCode) {
    this.removeImageResultCode = removeImageResultCode;
  }
}
