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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.model.Auth;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * Auth API.
 */
public class AuthApi extends ApiBase {
  public AuthApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/auth";
  }

  /**
   * Get auth status.
   *
   * @return {@link Auth} details.
   * @throws IOException
   */
  public Auth getAuthStatus() throws IOException {
    String path = getBasePath();

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(httpResponse,
        new TypeReference<Auth>() {
        });
  }

  /**
   * Get auth status asynchronously.
   *
   * @param responseCallback
   * @throws IOException
   */
  public void getAuthStatusAsync(final FutureCallback<Auth> responseCallback) throws  IOException {
    String path = getBasePath();

    getObjectByPathAsync(path, responseCallback, new TypeReference<Auth>() {
    });
  }
}
