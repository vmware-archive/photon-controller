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

package com.vmware.photon.controller.clustermanager.clients;

import com.vmware.photon.controller.api.client.RestClient;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import java.io.IOException;

/**
 * This class represents a simple Rest Client to call into Harbor Rest APIs to query the status of the cluster.
 */
public class HarborClient {
  private static final String HARBOR_STATUS_PATH = "/";
  private static final String HARBOR_PUBLIC_CA_CERT_PATH = "/static/resources/certs/ca.crt";

  private CloseableHttpAsyncClient httpClient;

  public HarborClient(CloseableHttpAsyncClient httpClient) {
    Preconditions.checkNotNull(httpClient);

    this.httpClient = httpClient;
  }

  /**
   * This method calls into the Harbor API endpoint and determine its status
   * based on http status.
   *
   * @param connectionString          connectionString of the Harbor Node
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void checkStatus(
      final String connectionString,
      final FutureCallback<Boolean> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback<HttpResponse> futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            callback.onSuccess(result.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
          }

          @Override
          public void failed(Exception ex) {
            callback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            callback.onFailure(
                new RuntimeException("Harbor status check was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, HARBOR_STATUS_PATH, null, futureCallback);
  }

  /**
   * This method gets the Harbor public CA certificate from Harbor registry.
   *
   * @param connectionString          connectionString of the Harbor Node
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getCACertificate(
      final String connectionString,
      final FutureCallback<String> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback<HttpResponse> futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            restClient.checkResponse(result, HttpStatus.SC_OK);
            try {
              callback.onSuccess(IOUtils.toString(result.getEntity().getContent(), "UTF-8"));
            } catch (IOException e) {
              callback.onFailure(e);
            }
          }

          @Override
          public void failed(Exception ex) {
            callback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            callback.onFailure(
                new RuntimeException("Harbor status check was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, HARBOR_PUBLIC_CA_CERT_PATH, null, futureCallback);
  }
}
