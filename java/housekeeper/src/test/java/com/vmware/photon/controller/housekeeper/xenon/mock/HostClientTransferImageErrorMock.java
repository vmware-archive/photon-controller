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

import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;

import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host client transfer image error mock.
 */
public class HostClientTransferImageErrorMock extends HostClientMock {

  private static final Logger logger = LoggerFactory.getLogger(HostClientTransferImageErrorMock.class);

  public HostClientTransferImageErrorMock() {
  }

  @Override
  public void transferImage(String imageId, String source, String destination,
                            ServerAddress destinationHost, AsyncMethodCallback callback) {
    logger.info("Host transferImage error invocation");
    callback.onError(new RuntimeException("transferImage error"));
  }
}
