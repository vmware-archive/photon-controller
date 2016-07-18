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

package com.vmware.photon.controller.api.client;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.protocol.BasicHttpContext;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tests {@link RestClient}.
 */
public class RestClientTest {
  final String endpoint = "http://1.1.1.1";
  final String path = "/v1/foo";
  final String uri = String.format("%s%s", endpoint, path);

  private CloseableHttpAsyncClient mockAsyncHttpClient;
  private HttpClient mockHttpClient;
  private RestClient restClient;
  private FutureCallback<HttpResponse> mockCallback;

  private ArgumentCaptor<HttpUriRequest> setup(RestClient.Method method, StringEntity payload) {
    mockAsyncHttpClient = mock(CloseableHttpAsyncClient.class);
    mockHttpClient = mock(HttpClient.class);
    restClient = new RestClient(endpoint, mockAsyncHttpClient, mockHttpClient);
    mockCallback = mock(FutureCallback.class);

    try {
      restClient.performAsync(method, path, payload, mockCallback);
    } catch (IOException e) {
      fail(e.getMessage());
    }

    ArgumentCaptor<HttpUriRequest> argumentCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);

    try {
      verify(
          mockAsyncHttpClient,
          times(1)).execute(argumentCaptor.capture(),
          any(BasicHttpContext.class),
          eq(mockCallback));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    return argumentCaptor;
  }

  @Test
  public void testPerformGet() {
    ArgumentCaptor<HttpUriRequest> argumentCaptor = setup(RestClient.Method.GET, null);

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.GET.toString()));
    assertEquals(request.getURI().toString(), uri);
  }

  @Test
  public void testPerformPut() {
    String payload = "{name: DUMMY}";
    ArgumentCaptor<HttpUriRequest> argumentCaptor =
        setup(RestClient.Method.PUT, new StringEntity(payload, ContentType.APPLICATION_JSON));

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.PUT.toString()));
    assertEquals(request.getURI().toString(), uri);

    HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request;
    String actualPayload = null;
    try {
      actualPayload = IOUtils.toString(httpEntityEnclosingRequest.getEntity().getContent());
    } catch (IOException e) {
      fail(e.getMessage());
    }

    assertEquals(actualPayload, payload);
  }

  @Test
  public void testPerformPost() {
    String payload = "{name: DUMMY}";
    ArgumentCaptor<HttpUriRequest> argumentCaptor =
        setup(RestClient.Method.POST, new StringEntity(payload, ContentType.APPLICATION_JSON));

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.POST.toString()));
    assertEquals(request.getURI().toString(), uri);

    HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request;
    String actualPayload = null;
    try {
      actualPayload = IOUtils.toString(httpEntityEnclosingRequest.getEntity().getContent());
    } catch (IOException e) {
      fail(e.getMessage());
    }

    assertEquals(actualPayload, payload);
  }

  @Test
  public void testPerformDelete() {
    ArgumentCaptor<HttpUriRequest> argumentCaptor = setup(RestClient.Method.DELETE, null);

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.DELETE.toString()));
    assertEquals(request.getURI().toString(), uri);
  }

  @Test
  public void testUpload() throws Throwable {
    mockHttpClient = mock(HttpClient.class);
    RestClient restClient = new RestClient(endpoint, null, mockHttpClient);
    ArgumentCaptor<HttpPost> httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);

    restClient.upload("path", "filename", ImmutableMap.<String, String>of());

    verify(mockHttpClient).execute(httpPostArgumentCaptor.capture());
    assertEquals(httpPostArgumentCaptor.getValue().getEntity().getClass(), MultipartEntity.class);
  }

  @Test
  public void testCheckResponseMatch() {
    RestClient restClient = new RestClient(endpoint, HttpAsyncClients.createDefault());

    HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(404);

    restClient.checkResponse(httpResponse, 404);
  }

  @Test
  public void testAddAuthHeader() {
    RestClient restClient = new RestClient(endpoint, HttpAsyncClients.createDefault(), "shared-secret");
    HttpUriRequest request = new HttpGet(endpoint + path);
    request = restClient.addAuthHeader(request);
    assertEquals(request.getHeaders(RestClient.AUTHORIZATION_HEADER)[0].toString(), RestClient.AUTHORIZATION_HEADER +
        ": " + RestClient.AUTHORIZATION_METHOD + "shared-secret");
  }

  @Test(expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "HTTP request failed with: 404")
  public void testCheckResponseMatchFail() {
    RestClient restClient = new RestClient(endpoint, HttpAsyncClients.createDefault());

    HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(404);

    restClient.checkResponse(httpResponse, 200);
  }

  @Test(expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "HTTP request failed with: 400, Response Body")
  public void testCheckResponseMatchFailWithBody() {
    RestClient restClient = new RestClient(endpoint, HttpAsyncClients.createDefault());

    HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(400);
    when(httpResponse.getEntity()).thenReturn(new HttpEntity() {
      @Override
      public boolean isRepeatable() {
        return false;
      }

      @Override
      public boolean isChunked() {
        return false;
      }

      @Override
      public long getContentLength() {
        return 0;
      }

      @Override
      public Header getContentType() {
        return null;
      }

      @Override
      public Header getContentEncoding() {
        return null;
      }

      @Override
      public InputStream getContent() throws IOException, IllegalStateException {
        return new ByteArrayInputStream("Response Body".getBytes());
      }

      @Override
      public void writeTo(OutputStream outstream) throws IOException {
      }

      @Override
      public boolean isStreaming() {
        return false;
      }

      @Override
      public void consumeContent() throws IOException {
      }
    });

    restClient.checkResponse(httpResponse, 200);
  }
}
