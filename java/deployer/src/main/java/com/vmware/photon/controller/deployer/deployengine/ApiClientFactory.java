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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * This class defines a factory which creates
 * {@link ApiClient} instances.
 */
public class ApiClientFactory {

  private ServerSet serverSet;
  private CloseableHttpAsyncClient httpClient;
  private String sharedSecret;
  private boolean isAuthEnabled;

  public ApiClientFactory(ServerSet serverSet,
                          CloseableHttpAsyncClient httpClient,
                          String sharedSecret,
                          boolean isAuthEnabled) {

    this.serverSet = serverSet;
    this.httpClient = httpClient;
    this.sharedSecret = sharedSecret;
    this.isAuthEnabled = isAuthEnabled;
  }

  public ApiClient create() {
    String endpoint;
    String protocol;
    if (this.isAuthEnabled) {
      protocol = "https";
    } else {
      protocol = "http";
    }
    try {
      endpoint = ServiceUtils.createUriFromServerSet(serverSet, null, protocol).toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return create(endpoint);
  }

  public ApiClient create(String endpoint) {
    String protocol;
    if (this.isAuthEnabled) {
      protocol = "https";
    } else {
      protocol = "http";
    }
    try {
      return new ApiClient(endpoint, httpClient, sharedSecret, protocol);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
