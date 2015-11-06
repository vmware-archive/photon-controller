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

package com.vmware.photon.controller.housekeeper.dcp.mock;

import com.vmware.photon.controller.host.gen.Host;

import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host client mock.
 */
public class HostClientImageInfoErrorMock extends HostClientMock {

  private static final Logger logger = LoggerFactory.getLogger(HostClientImageInfoErrorMock.class);

  public HostClientImageInfoErrorMock() {
  }

  @Override
  public void getImageInfo(String imageId, String datastoreId, AsyncMethodCallback<Host.AsyncClient.get_image_info_call>
      callback) {
    logger.info("Host imageInfo error invocation");
    callback.onError(new RuntimeException("imageInfo error"));
  }
}
