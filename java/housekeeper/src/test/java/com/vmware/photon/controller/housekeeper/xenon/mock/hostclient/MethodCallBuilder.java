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

import com.vmware.photon.controller.host.gen.GetDeletedImagesResponse;
import com.vmware.photon.controller.host.gen.GetInactiveImagesResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.StartImageScanResponse;
import com.vmware.photon.controller.host.gen.StartImageSweepResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Implements helpers to create method call mock objects.
 */
public class MethodCallBuilder {

  /**
   * Builds a mock method call object for the stat_image_scan host method.
   *
   * @param response
   * @return
   */
  public static Host.AsyncClient.start_image_scan_call buildStartImageScanMethodCall(StartImageScanResponse response) {
    Host.AsyncClient.start_image_scan_call call = mock(Host.AsyncClient.start_image_scan_call.class);

    try {
      when(call.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock call.getResult");
    }

    return call;
  }

  /**
   * Builds a mock method call object for the get_inactive_images host method.
   *
   * @param response
   * @return
   */
  public static Host.AsyncClient.get_inactive_images_call buildGetInactiveImagesMethodCall(
      GetInactiveImagesResponse response) {
    Host.AsyncClient.get_inactive_images_call call = mock(Host.AsyncClient.get_inactive_images_call.class);

    try {
      when(call.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock call.getResult");
    }

    return call;
  }

  /**
   * Builds a mock method call object for the stat_image_scan host method.
   *
   * @param response
   * @return
   */
  public static Host.AsyncClient.start_image_sweep_call buildStartImageSweepMethodCall(
      StartImageSweepResponse response) {
    Host.AsyncClient.start_image_sweep_call call = mock(Host.AsyncClient.start_image_sweep_call.class);

    try {
      when(call.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock call.getResult");
    }

    return call;
  }

  /**
   * Builds a mock method call object for the get_deleted_images host method.
   *
   * @param response
   * @return
   */
  public static Host.AsyncClient.get_deleted_images_call buildGetDeletedImagesMethodCall(
      GetDeletedImagesResponse response) {
    Host.AsyncClient.get_deleted_images_call call = mock(Host.AsyncClient.get_deleted_images_call.class);

    try {
      when(call.getResult()).thenReturn(response);
    } catch (Exception e) {
      throw new RuntimeException("Failed to mock call.getResult");
    }

    return call;
  }
}
