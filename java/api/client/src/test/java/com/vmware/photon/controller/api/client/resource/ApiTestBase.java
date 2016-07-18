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

package com.vmware.photon.controller.api.client.resource;

import com.vmware.photon.controller.api.client.RestClient;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Common setup functions for unit tests.
 */
public class ApiTestBase {
  private CloseableHttpAsyncClient asyncHttpClient;
  private HttpClient httpClient;
  protected RestClient restClient;

  public static final int COUNTDOWNLATCH_AWAIT_TIMEOUT = 10;
  public static final int ARGUMENT_INDEX_TWO = 2;

  public final void setupMocks(String serializedResponse, int responseCode) throws IOException {
    this.asyncHttpClient = mock(CloseableHttpAsyncClient.class);
    this.httpClient = mock(HttpClient.class);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return null;
      }
    }).when(this.asyncHttpClient).close();

    this.restClient = new RestClient("http://1.1.1.1", this.asyncHttpClient, this.httpClient);

    final HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(responseCode);
    when(httpResponse.getEntity()).thenReturn(new StringEntity(serializedResponse, ContentType.APPLICATION_JSON));

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

    when(this.httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);

    when(this.asyncHttpClient.execute(any(HttpUriRequest.class),
        any(BasicHttpContext.class),
        any(FutureCallback.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArguments()[ARGUMENT_INDEX_TWO] != null) {
                  ((FutureCallback<HttpResponse>) invocation.getArguments()[ARGUMENT_INDEX_TWO])
                      .completed(httpResponse);
                }
                return httpResponseFuture;
              }
            }
        );
  }

  public final void setupMocksForPagination(String serializedResponse, String serializedResponseForNextPage,
                                            String nextPageLink, int responseCode) throws IOException {
    this.asyncHttpClient = mock(CloseableHttpAsyncClient.class);
    this.httpClient = mock(HttpClient.class);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return null;
      }
    }).when(this.asyncHttpClient).close();

    this.restClient = new RestClient("http://1.1.1.1", this.asyncHttpClient, this.httpClient);

    final HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(responseCode);
    when(httpResponse.getEntity()).thenReturn(new StringEntity(serializedResponse, ContentType.APPLICATION_JSON));

    final HttpResponse httpResponseForNextPage = mock(HttpResponse.class);
    StatusLine statusLineForNextPage = mock(StatusLine.class);
    when(httpResponseForNextPage.getStatusLine()).thenReturn(statusLineForNextPage);
    when(statusLineForNextPage.getStatusCode()).thenReturn(responseCode);
    when(httpResponseForNextPage.getEntity())
        .thenReturn(new StringEntity(serializedResponseForNextPage, ContentType.APPLICATION_JSON));

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

    final Future<HttpResponse> httpResponseFutureForNextPage = new Future<HttpResponse>() {
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
        return httpResponseForNextPage;
      }

      @Override
      public HttpResponse get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return httpResponseForNextPage;
      }
    };

    when(this.httpClient.execute(any(HttpUriRequest.class))).thenAnswer(new Answer<HttpResponse>() {
      @Override
      public HttpResponse answer(InvocationOnMock invocation) throws Throwable {
        HttpUriRequest httpUriRequest = (HttpUriRequest) invocation.getArguments()[0];
        if (httpUriRequest.getURI().toString().contains(nextPageLink)) {
          return httpResponseForNextPage;
        }
        return httpResponse;
      }
    });

    when(this.asyncHttpClient.execute(any(HttpUriRequest.class),
        any(BasicHttpContext.class),
        any(FutureCallback.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                HttpUriRequest httpUriRequest = (HttpUriRequest) invocation.getArguments()[0];
                if (httpUriRequest.getURI().toString().contains(nextPageLink)) {
                  if (invocation.getArguments()[ARGUMENT_INDEX_TWO] != null) {
                    ((FutureCallback<HttpResponse>) invocation.getArguments()[ARGUMENT_INDEX_TWO])
                        .completed(httpResponseForNextPage);
                  }
                  return httpResponseFutureForNextPage;
                }

                if (invocation.getArguments()[ARGUMENT_INDEX_TWO] != null) {
                  ((FutureCallback<HttpResponse>) invocation.getArguments()[ARGUMENT_INDEX_TWO])
                      .completed(httpResponse);
                }
                return httpResponseFuture;
              }
            }
        );
  }

  public final void setupMocksToThrow(final Exception exceptionToThrow) throws IOException {
    this.asyncHttpClient = mock(CloseableHttpAsyncClient.class);
    this.httpClient = mock(HttpClient.class);
    this.restClient = new RestClient("http://1.1.1.1", this.asyncHttpClient, httpClient);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        return null;
      }
    }).when(this.asyncHttpClient).close();

    // async http request
    when(this.asyncHttpClient.execute(any(HttpUriRequest.class),
        any(BasicHttpContext.class),
        (FutureCallback<HttpResponse>) any()))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                throw exceptionToThrow;
              }
            }
        );
    when(this.httpClient.execute(any(HttpUriRequest.class))).thenThrow(exceptionToThrow);
  }
}
