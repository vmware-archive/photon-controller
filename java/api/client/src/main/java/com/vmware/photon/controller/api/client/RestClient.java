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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Rest client.
 */
public class RestClient {

  // OAuth2.0 Authorization Request Header: http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-20#section-2.1
  public static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

  // OAuth2.0 Bearer Token: http://self-issued.info/docs/draft-ietf-oauth-v2-bearer.html
  public static final String AUTHORIZATION_METHOD = "Bearer ";

  /**
   * Possible http verbs.
   */
  public enum Method {
    GET,
    PUT,
    POST,
    DELETE;
  }

  private final CloseableHttpAsyncClient asyncClient;
  private final String target;
  private String sharedSecret;

  // For testing only
  private HttpClient mockHttpClient;

  /**
   * Initializes RestClient with a mock http async client.
   *
   * @param asyncClient
   */
  public RestClient(String target, CloseableHttpAsyncClient asyncClient) {
    if (null == asyncClient) {
      throw new IllegalArgumentException("Client cannot be null");
    }
    if (null == target) {
      throw new IllegalArgumentException("Target cannot be null");
    }
    this.target = target;
    this.asyncClient = asyncClient;
  }

  public RestClient(String target, CloseableHttpAsyncClient asyncClient, String sharedSecret) {
    if (null == asyncClient) {
      throw new IllegalArgumentException("Client cannot be null");
    }
    if (null == target) {
      throw new IllegalArgumentException("Target cannot be null");
    }
    if (null == sharedSecret) {
      throw new IllegalArgumentException("SharedSecret cannot be null");
    }
    this.target = target;
    this.asyncClient = asyncClient;
    this.sharedSecret = sharedSecret;
  }

  public RestClient(String target, CloseableHttpAsyncClient asyncClient, HttpClient mockHttpClient) {
    this.target = target;
    this.asyncClient = asyncClient;
    this.mockHttpClient = mockHttpClient;
  }

  /**
   * Perform an http request synchronously.
   * @param method
   * @param path
   * @param payload
   * @return
   * @throws IOException
   */
  public HttpResponse perform(final Method method, final String path, final HttpEntity payload)
      throws IOException {
    try {
      return performAsync(method, path, payload, null /* callback */).get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Perform an http request asynchronously.
   * @param method
   * @param path
   * @param payload
   * @param responseHandler
   * @return
   * @throws IOException
   */
  public Future<HttpResponse> performAsync(final Method method, final String path, final HttpEntity payload,
                                           final FutureCallback<HttpResponse> responseHandler) throws IOException {
    HttpUriRequest request = createHttpRequest(method, path, payload);
    return asyncClient.execute(request, new BasicHttpContext(), responseHandler);
  }


  private HttpUriRequest createHttpRequest(final Method method, final String path, final HttpEntity payload) throws
      IOException {
    String uri = target + path;
    HttpUriRequest request = null;

    switch (method.name().toLowerCase()) {
      case "get":
        request = new HttpGet(uri);
        break;

      case "put":
        request = new HttpPut(uri);
        ((HttpPut) request).setEntity(payload);
        break;

      case "post":
        request = new HttpPost(uri);
        ((HttpPost) request).setEntity(payload);
        break;

      case "delete":
        request = new HttpDelete(uri);
        break;

      default:
        throw new RuntimeException("Unknown method: " + method);
    }

    request = addAuthHeader(request);
    return request;
  }

  @VisibleForTesting
  protected HttpUriRequest addAuthHeader(HttpUriRequest request) {
    if (sharedSecret != null) {
      request.addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_METHOD + sharedSecret);
    }
    return request;
  }

  public void checkResponse(HttpResponse httpResponse, int expected) {
    int statusCode = httpResponse.getStatusLine().getStatusCode();

    if (statusCode != expected) {
      StringBuilder msg = new StringBuilder();
      msg.append("HTTP request failed with: ");
      msg.append(statusCode);
      if (httpResponse.getEntity() != null) {
        try {
          msg.append(", ");
          msg.append(EntityUtils.toString(httpResponse.getEntity()));
        } catch (IOException e) {
          // ignore exception here and use partial error message.
        }
      }
      throw new RuntimeException(msg.toString());
    }
  }

  public <T> T parseHttpResponse(HttpResponse response, TypeReference<T> classType) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(response.getEntity().getContent(), classType);
  }

  public HttpResponse upload(String path, String inputFileName, Map<String, String> arguments) throws IOException {
    return upload(path, new FileBody(new File(inputFileName), "application/octet-stream"), arguments);
  }

  public HttpResponse upload(String path, FileBody fileBody, Map<String, String> arguments) throws IOException {
    HttpClient httpClient = getHttpClient();
    HttpPost httpPost = new HttpPost(this.target + path);
    if (this.sharedSecret != null) {
      httpPost.addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_METHOD + this.sharedSecret);
    }

    MultipartEntity multipartEntity = new MultipartEntity();
    for (Map.Entry<String, String> argument : arguments.entrySet()) {
      StringBody stringBody = new StringBody(argument.getValue());
      multipartEntity.addPart(argument.getKey(), stringBody);
    }

    multipartEntity.addPart("file", fileBody);
    httpPost.setEntity(multipartEntity);
    return httpClient.execute(httpPost);
  }

  private HttpClient getHttpClient() {
    if (mockHttpClient != null) {
      return mockHttpClient;
    }
    return HttpClients.createDefault();
  }
}
