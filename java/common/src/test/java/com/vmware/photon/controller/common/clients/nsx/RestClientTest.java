/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.clients.nsx;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.protocol.BasicHttpContext;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeClass;
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

import java.io.IOException;

/**
 * Tests for {@link RestClient}.
 */
public class RestClientTest {
  private String target;
  private String username;
  private String password;
  private String path;
  private String payload;

  @BeforeClass
  public void setUp() {
    target = "https://1.2.3.4";
    username = "username";
    password = "password";
    path = "/v1/foo";
    payload = "{name: DUMMY}";
  }

  private ArgumentCaptor<HttpUriRequest> setup(RestClient.Method method, HttpEntity payload) {
    CloseableHttpAsyncClient mockAsyncClient = mock(CloseableHttpAsyncClient.class);
    FutureCallback<HttpResponse> mockCallback = mock(FutureCallback.class);
    RestClient restClient = new RestClient(target, username, password, mockAsyncClient);

    try {
      restClient.sendAsync(method, path, payload, mockCallback);
    } catch (IOException e) {
      fail(e.getMessage());
    }

    ArgumentCaptor<HttpUriRequest> argumentCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);

    try {
      verify(
          mockAsyncClient,
          times(1)).execute(argumentCaptor.capture(),
          any(BasicHttpContext.class),
          eq(mockCallback));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    return argumentCaptor;
  }

  @Test
  public void testSendGet() {
    ArgumentCaptor<HttpUriRequest> argumentCaptor = setup(RestClient.Method.GET, null);

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.GET.toString()));
    assertEquals(request.getURI().toString(), target + path);
  }

  @Test
  public void testPerformPut() {
    String payload = "{name: DUMMY}";
    ArgumentCaptor<HttpUriRequest> argumentCaptor =
        setup(RestClient.Method.PUT, new StringEntity(payload, ContentType.APPLICATION_JSON));

    HttpUriRequest request = argumentCaptor.getValue();
    assertNotNull(request);
    assertTrue(request.getMethod().equalsIgnoreCase(RestClient.Method.PUT.toString()));
    assertEquals(request.getURI().toString(), target + path);

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
    assertEquals(request.getURI().toString(), target + path);

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
    assertEquals(request.getURI().toString(), target + path);
  }

  @Test
  public void testCheckResponseMatch() {
    RestClient restClient = new RestClient(target, username, password, HttpAsyncClients.createDefault());

    HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(404);

    restClient.check(httpResponse, 404);
  }

  @Test(expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "HTTP request failed with: 404")
  public void testCheckResponseMismatch() {
    RestClient restClient = new RestClient(target, username, password, HttpAsyncClients.createDefault());

    HttpResponse httpResponse = mock(HttpResponse.class);
    StatusLine statusLine = mock(StatusLine.class);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(404);

    restClient.check(httpResponse, 200);
  }
}
