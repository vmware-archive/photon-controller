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

package com.vmware.photon.controller.nsxclient;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A RESTful client which utilizes basic HTTP methods.
 */
public class RestClient {

  /**
   * Possible HTTP methods.
   */
  public enum Method {
    GET,
    PUT,
    POST,
    DELETE
  }

  private final String target;
  private final HttpClientContext clientContext;
  private final CloseableHttpAsyncClient asyncClient;

  /**
   * Constructs a RestClient.
   */
  public RestClient(String target, String username, String password) {
    this(target, username, password, null);
  }

  /**
   * Constructs a RestClient.
   */
  public RestClient(String target, String username, String password, CloseableHttpAsyncClient asyncClient) {
    checkNotNull(target, "target cannot be null");
    checkNotNull(username, "username cannot be null");
    checkNotNull(password, "password cannot be null");

    this.target = target;
    this.clientContext = getHttpClientContext(target, username, password);
    this.asyncClient = asyncClient == null ? getHttpClient() : asyncClient;
  }

  /**
   * Sends a HTTP request synchronously on the given path with payload.
   */
  public HttpResponse send(final Method method, final String path, final HttpEntity payload) {
    try {
      return sendAsync(method, path, payload, null /* callback */).get();
    } catch (InterruptedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sends a HTTP request asynchronously on the given path with payload.
   */
  public Future<HttpResponse> sendAsync(final Method method, final String path, final HttpEntity payload,
                                           final FutureCallback<HttpResponse> responseHandler) throws IOException {
    HttpUriRequest request = getHttpRequest(method, path, payload);
    return this.asyncClient.execute(request, this.clientContext, responseHandler);
  }

  /**
   * Performs sanity check on the HTTP response code.
   */
  public void check(HttpResponse httpResponse, int expectedResponseCode) {
    int statusCode = httpResponse.getStatusLine().getStatusCode();

    if (statusCode != expectedResponseCode) {
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

  /**
   * Creates a HTTP request object.
   */
  private HttpUriRequest getHttpRequest(final Method method, final String path, final HttpEntity payload) throws
      IOException {
    String uri = target + path;
    HttpUriRequest request;

    switch (method) {
      case GET:
        request = new HttpGet(uri);
        break;

      case PUT:
        request = new HttpPut(uri);
        ((HttpPut) request).setEntity(payload);
        break;

      case POST:
        request = new HttpPost(uri);
        ((HttpPost) request).setEntity(payload);
        break;

      case DELETE:
        request = new HttpDelete(uri);
        break;

      default:
        throw new RuntimeException("Unknown method: " + method);
    }

    return request;
  }

  /**
   * Creates a HTTP client context with preemptive basic authentication.
   */
  private HttpClientContext getHttpClientContext(String target, String username, String password) {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY,
        new UsernamePasswordCredentials(username, password));
    HttpHost targetHost = HttpHost.create(target);
    AuthCache authCache = new BasicAuthCache();
    authCache.put(targetHost, new BasicScheme());

    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credentialsProvider);
    context.setAuthCache(authCache);

    return context;
  }

  /**
   * Creates a HTTP client.
   */
  private CloseableHttpAsyncClient getHttpClient() {
    try {
      SSLContext sslcontext = SSLContexts.custom()
          .loadTrustMaterial((chain, authtype) -> true)
          .build();

      CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClientBuilder.create()
          .setHostnameVerifier(SSLIOSessionStrategy.ALLOW_ALL_HOSTNAME_VERIFIER)
          .setSSLContext(sslcontext)
          .build();
      httpAsyncClient.start();
      return httpAsyncClient;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
