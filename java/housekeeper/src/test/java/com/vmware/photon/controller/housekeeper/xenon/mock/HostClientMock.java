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

package com.vmware.photon.controller.housekeeper.xenon.mock;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.CopyImageResponse;
import com.vmware.photon.controller.host.gen.CopyImageResultCode;
import com.vmware.photon.controller.host.gen.GetDeletedImagesResponse;
import com.vmware.photon.controller.host.gen.GetImagesResponse;
import com.vmware.photon.controller.host.gen.GetImagesResultCode;
import com.vmware.photon.controller.host.gen.GetInactiveImagesResponse;
import com.vmware.photon.controller.host.gen.GetMonitoredImagesResultCode;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.StartImageOperationResultCode;
import com.vmware.photon.controller.host.gen.StartImageScanResponse;
import com.vmware.photon.controller.host.gen.StartImageSweepResponse;
import com.vmware.photon.controller.host.gen.TransferImageResponse;
import com.vmware.photon.controller.host.gen.TransferImageResultCode;
import com.vmware.photon.controller.housekeeper.xenon.mock.hostclient.MethodCallBuilder;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;

import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host client mock.
 */
public class HostClientMock extends HostClient {

  private static final Logger logger = LoggerFactory.getLogger(HostClientMock.class);

  private static final String INACTIVE_IMAGES_KEY_FORMAT_STRING = "INACTIVE_IMAGES_%s";

  private static final String DELETED_IMAGES_KEY_FORMAT_STRING = "DELETED_IMAGES_%s";

  private static final String DATASTORE_GC_KEY_FORMAT_STRING = "DSGC_%s";

  private Map<String, Object> state;

  private CopyImageResultCode copyImageResultCode;

  private GetImagesResultCode getImagesResultCode;

  private TransferImageResultCode transferImageResultCode;

  private List<String> imagesForGetImagesRequest;

  /**
   * Fine grained images being returned for a specific datastoreId.
   */
  private Map<String, List<String>> imageListForGetImagesRequest;

  public HostClientMock() {
    super(mock(ClientProxyFactory.class), mock(ClientPoolFactory.class));
    state = Collections.synchronizedMap(new HashMap<>());
    copyImageResultCode = CopyImageResultCode.OK;
    getImagesResultCode = GetImagesResultCode.OK;
    transferImageResultCode = TransferImageResultCode.OK;

    imagesForGetImagesRequest = new ArrayList<>();
  }

  public void setCopyImageResultCode(CopyImageResultCode copyImageResultCode) {
    this.copyImageResultCode = copyImageResultCode;
  }

  public void setTransferImageResultCode(TransferImageResultCode transferImageResultCode) {
    this.transferImageResultCode = transferImageResultCode;
  }

  public void setGetImagesResultCode(GetImagesResultCode getImagesResultCode) {
    this.getImagesResultCode = getImagesResultCode;
  }

  public void setImagesForGetImagesRequest(List<String> imagesForGetImagesRequest) {
    this.imagesForGetImagesRequest = imagesForGetImagesRequest;
  }

  public void setImageListForGetImagesRequest(Map<String, List<String>> imageListForGetImagesRequest) {
    this.imageListForGetImagesRequest = imageListForGetImagesRequest;
  }

  public void setInactiveImages(String datastore, List<InactiveImageDescriptor> imageList) {
    this.state.put(String.format(INACTIVE_IMAGES_KEY_FORMAT_STRING, datastore), imageList);
  }

  @Override
  public void copyImage(String imageId, String source, String destination, AsyncMethodCallback callback) {
    if (source.equals(destination)) {
      fail("Same source and destination should not be passed to call HostClient");
    }

    logger.info("Host copyImage complete invocation");
    CopyImageResponse response = new CopyImageResponse();
    response.setResult(copyImageResultCode);

    Host.AsyncClient.copy_image_call copyImageCall = mock(Host.AsyncClient.copy_image_call.class);
    try {
      when(copyImageCall.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock copyImageCall.getResult");
    }
    callback.onComplete(copyImageCall);
  }

  @Override
  public void transferImage(String imageId, String source, String destination,
                            ServerAddress destinationHost, AsyncMethodCallback callback) {
    if (source.equals(destination)) {
      fail("Same source and destination should not be passed to call HostClient");
    }

    logger.info("Host transferImage complete invocation");
    TransferImageResponse response = new TransferImageResponse();
    response.setResult(transferImageResultCode);

    Host.AsyncClient.transfer_image_call transferImageCall = mock(Host.AsyncClient.transfer_image_call.class);
    try {
      when(transferImageCall.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock copyImageCall.getResult");
    }
    callback.onComplete(transferImageCall);
  }

  @Override
  public void setIpAndPort(String ip, int port) {
    // do nothing as we do not want to open a real connection.
  }

  @Override
  public void getImages(String datastoreId, AsyncMethodCallback<Host.AsyncClient.get_images_call> callback) {
    logger.info("Host getImages complete invocation");
    GetImagesResponse response = new GetImagesResponse(getImagesResultCode);
    if (imageListForGetImagesRequest != null &&
        imageListForGetImagesRequest.containsKey(datastoreId)) {
      response.setImage_ids(imageListForGetImagesRequest.get(datastoreId));
    } else {
      response.setImage_ids(imagesForGetImagesRequest);
    }
    Host.AsyncClient.get_images_call getImagesCall = mock(Host.AsyncClient.get_images_call.class);
    try {
      when(getImagesCall.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock getImagesCall.getResult");
    }
    callback.onComplete(getImagesCall);
  }

  @Override
  public void startImageScan(String datastore, Long scanRate, Long timeout,
                             AsyncMethodCallback<Host.AsyncClient.start_image_scan_call> callback) {
    this.incrementImageGCCounter(datastore);

    StartImageScanResponse response = new StartImageScanResponse(StartImageOperationResultCode.OK);
    callback.onComplete(MethodCallBuilder.buildStartImageScanMethodCall(response));
  }

  @Override
  public void getInactiveImages(String datastore,
                                AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call> callback) {
    this.checkImageGCCounter(datastore, 1L);

    if (null == this.state.get("isGetInactiveImagesFirstCall")) {
      this.state.put("isGetInactiveImagesFirstCall", false);
      GetInactiveImagesResponse response =
          new GetInactiveImagesResponse(GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS);
      callback.onComplete(MethodCallBuilder.buildGetInactiveImagesMethodCall(response));
      return;
    }

    GetInactiveImagesResponse response = new GetInactiveImagesResponse(GetMonitoredImagesResultCode.OK);
    response.setImage_descs(
        (List<InactiveImageDescriptor>) this.state.get(String.format(INACTIVE_IMAGES_KEY_FORMAT_STRING, datastore)));
    callback.onComplete(MethodCallBuilder.buildGetInactiveImagesMethodCall(response));
  }

  @Override
  public void startImageSweep(String datastore, List<InactiveImageDescriptor> images, Long sweepRate, Long timeout,
                              AsyncMethodCallback<Host.AsyncClient.start_image_sweep_call> callback) {
    this.checkImageGCCounter(datastore, 1L);
    this.state.put(String.format(DELETED_IMAGES_KEY_FORMAT_STRING, datastore), images);

    StartImageSweepResponse response = new StartImageSweepResponse(StartImageOperationResultCode.OK);
    callback.onComplete(MethodCallBuilder.buildStartImageSweepMethodCall(response));
  }

  @Override
  public void getDeletedImages(String datastore,
                               AsyncMethodCallback<Host.AsyncClient.get_deleted_images_call> callback) {
    this.checkImageGCCounter(datastore, 1L);

    if (null == this.state.get("isGetDeletedImagesFirstCall")) {
      this.state.put("isGetDeletedImagesFirstCall", false);
      GetDeletedImagesResponse response =
          new GetDeletedImagesResponse(GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS);
      callback.onComplete(MethodCallBuilder.buildGetDeletedImagesMethodCall(response));
      return;
    }

    GetDeletedImagesResponse response = new GetDeletedImagesResponse(GetMonitoredImagesResultCode.OK);
    response.setImage_descs(
        (List<InactiveImageDescriptor>) this.state.get(String.format(DELETED_IMAGES_KEY_FORMAT_STRING, datastore)));
    callback.onComplete(MethodCallBuilder.buildGetDeletedImagesMethodCall(response));
  }

  private void incrementImageGCCounter(String datastore) {
    Long counter = 0L;
    String key = String.format(DATASTORE_GC_KEY_FORMAT_STRING, datastore);
    if (this.state.containsKey(key)) {
      counter = (Long) this.state.get(key);
      counter++;
    }

    this.state.put(key, counter);
  }

  private void checkImageGCCounter(String datastore, Long value) {
    String key = String.format(DATASTORE_GC_KEY_FORMAT_STRING, datastore);
    if (!this.state.containsKey(key)) {
      throw new RuntimeException("No GC operation was started for " + datastore);
    }

    Long count = (Long) this.state.get(key);
    if (count > value) {
      throw new RuntimeException("GC operation already in progress");
    }
  }
}
