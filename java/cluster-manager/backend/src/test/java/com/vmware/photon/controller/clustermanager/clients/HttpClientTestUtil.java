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

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility for HttpClient tests.
 */
public class HttpClientTestUtil {
  @SuppressWarnings("unchecked")
  public static CloseableHttpAsyncClient setupMocks(String serializedResponse, int responseCode)
      throws IOException {
    CloseableHttpAsyncClient asyncHttpClient = Mockito.mock(CloseableHttpAsyncClient.class);

    Mockito.doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return null;
      }
    }).when(asyncHttpClient).close();

    final HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
    StatusLine statusLine = Mockito.mock(StatusLine.class);
    Mockito.when(httpResponse.getStatusLine()).thenReturn(statusLine);
    Mockito.when(statusLine.getStatusCode()).thenReturn(responseCode);
    Mockito.when(httpResponse.getEntity())
        .thenReturn(new StringEntity(serializedResponse, ContentType.APPLICATION_JSON));

    final Future<HttpResponse> httpResponseFuture = new Future<HttpResponse>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }
      @Override
      public boolean isCancelled() {
        return false;
      }
      @Override
      public boolean isDone() {
        return true;
      }
      @Override
      public HttpResponse get() throws InterruptedException, ExecutionException {
        return httpResponse;
      }
      @Override
      public HttpResponse get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return httpResponse;
      }
    };

    Mockito.when(asyncHttpClient.execute(Matchers.any(HttpUriRequest.class),
        Matchers.any(BasicHttpContext.class),
        Matchers.any(org.apache.http.concurrent.FutureCallback.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArguments()[2] != null) {
                  ((org.apache.http.concurrent.FutureCallback<HttpResponse>) invocation.getArguments()[2])
                      .completed(httpResponse);
                }
                return httpResponseFuture;
              }
            }
        );
    return asyncHttpClient;
  }

  @SuppressWarnings("unchecked")
  public static CloseableHttpAsyncClient setupMocksToThrowInCallback(final Exception exceptionToThrow)
      throws IOException {
    CloseableHttpAsyncClient asyncHttpClient = Mockito.mock(CloseableHttpAsyncClient.class);

    Mockito.doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return null;
      }
    }).when(asyncHttpClient).close();

    final Future<HttpResponse> httpResponseFuture = new Future<HttpResponse>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }
      @Override
      public boolean isCancelled() {
        return false;
      }
      @Override
      public boolean isDone() {
        return true;
      }
      @Override
      public HttpResponse get() throws InterruptedException, ExecutionException {
        return null;
      }
      @Override
      public HttpResponse get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }
    };

    // async http request
    Mockito.when(asyncHttpClient.execute(Matchers.any(HttpUriRequest.class),
        Matchers.any(BasicHttpContext.class),
        (org.apache.http.concurrent.FutureCallback<HttpResponse>) Matchers.any()))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArguments()[2] != null) {
                  ((org.apache.http.concurrent.FutureCallback<HttpResponse>) invocation.getArguments()[2])
                      .failed(exceptionToThrow);
                }
                return httpResponseFuture;
              }
            }
        );
    return asyncHttpClient;
  }
}
