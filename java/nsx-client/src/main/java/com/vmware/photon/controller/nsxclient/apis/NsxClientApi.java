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

package com.vmware.photon.controller.nsxclient.apis;

import com.vmware.photon.controller.nsxclient.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * This is the base class of the NSX client API implementations, and
 * it holds the shared functions. Each API implemented, e.g., LogicalSwitchApi
 * should extends this class.
 */
public class NsxClientApi {
  private static final int WAIT_ITERATION_SLEEP_MILLISECONDS = 500;
  private static final int WAIT_ITERATION_COUNT = 30;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  protected final String basePath = "/api/v1";

  /**
   * Constructs a NSX client api base class.
   */
  public NsxClientApi(RestClient restClient) {
    checkNotNull(restClient, "restClient cannot be null");

    this.restClient = restClient;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Performs a POST HTTP request to NSX.
   */
  protected <T> T post(final String path,
                     final HttpEntity payload,
                     final int expectedResponseStatus,
                     final TypeReference<T> typeReference) throws IOException {
    HttpResponse result = restClient.send(
        RestClient.Method.POST,
        path,
        payload);

    restClient.check(result, expectedResponseStatus);
    return deserializeObjectFromJson(result.getEntity(), typeReference);
  }

  /**
   * Performs a POST HTTP request to NSX.
   */
  protected <T> void postAsync(final String path,
                             final HttpEntity payload,
                             final int expectedResponseStatus,
                             final TypeReference<T> typeReference,
                             final FutureCallback<T> responseCallback) throws IOException {
    restClient.sendAsync(
        RestClient.Method.POST,
        path,
        payload,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            T ret = null;
            try {
              restClient.check(result, expectedResponseStatus);
              ret = deserializeObjectFromJson(result.getEntity(), typeReference);
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            if (ret != null) {
              responseCallback.onSuccess(ret);
            }
          }

          @Override
          public void failed(Exception ex) {
            responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("postAsync %s was cancelled",
                path)));
          }
        }
    );
  }

  /**
   * Performs a GET HTTP request to NSX.
   */
  protected <T> T get(final String path,
                    final int expectedResponseStatus,
                    final TypeReference<T> typeReference) throws IOException {
    HttpResponse result = restClient.send(
        RestClient.Method.GET,
        path,
        null);

    restClient.check(result, expectedResponseStatus);
    return deserializeObjectFromJson(result.getEntity(), typeReference);
  }

  /**
   * Performs a GET HTTP request to NSX.
   */
  protected <T> void getAsync(final String path,
                            final int expectedResponseStatus,
                            final TypeReference<T> typeReference,
                            final FutureCallback<T> responseCallback) throws IOException {
    restClient.sendAsync(
        RestClient.Method.GET,
        path,
        null,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            T ret = null;
            try {
              restClient.check(result, expectedResponseStatus);
              ret = deserializeObjectFromJson(result.getEntity(), typeReference);
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            if (ret != null) {
              responseCallback.onSuccess(ret);
            }
          }

          @Override
          public void failed(Exception ex) {
            responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("getAsync %s was cancelled",
                path)));
          }
        }
    );
  }

  /**
   * Performs a DELETE HTTP request to NSX.
   */
  protected void delete(final String path,
                      final int expectedResponseStatus) throws IOException {
    HttpResponse result = restClient.send(
        RestClient.Method.DELETE,
        path,
        null);

    restClient.check(result, expectedResponseStatus);
  }

  /**
   * Performs a DELETE HTTP request to NSX.
   */
  protected void deleteAsync(final String path,
                           final int expectedResponseStatus,
                           final FutureCallback<Void> responseCallback) throws IOException {
    restClient.sendAsync(
        RestClient.Method.DELETE,
        path,
        null,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            try {
              restClient.check(result, expectedResponseStatus);
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            responseCallback.onSuccess(null);
          }

          @Override
          public void failed(Exception ex) {
            responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("deleteAsync %s was cancelled",
                path)));
          }
        }
    );
  }

  protected <T> T waitForConfigurationFinished(final String url,
                                               final int expectedResponseStatus,
                                               final TypeReference<T> typeReference,
                                               final Predicate<T> predicate) throws Exception {

    return waitForConfigurationFinished(url,
        expectedResponseStatus,
        typeReference,
        predicate,
        this.WAIT_ITERATION_COUNT,
        this.WAIT_ITERATION_SLEEP_MILLISECONDS);
  }

  /**
   * When an NSX equipment is created, SC_CREATED is returned.
   * Therefore, the configuration state needs to be checked to
   * determine whether keep waiting is needed.
   */
  protected <T> T waitForConfigurationFinished(final String url,
                                               final int expectedResponseStatus,
                                               final TypeReference<T> typeReference,
                                               final Predicate<T> predicate,
                                               final int iterationCount,
                                               final int iterationSleepMilliseconds) throws Exception {

    for (int i = 0; i < iterationCount; i++) {
      T response = get(url, expectedResponseStatus, typeReference);
      if (predicate.test(response)) {
        return response;
      }
      TimeUnit.MILLISECONDS.sleep(iterationSleepMilliseconds);
    }

    throw new TimeoutException("Timed out when waiting for the configuration to be finished");
  }

  /**
   * Serializes HTTP request to JSON string.
   */
  protected StringEntity serializeObjectAsJson(Object o) throws JsonProcessingException {
    String payload = objectMapper.writeValueAsString(o);
    return new StringEntity(payload, ContentType.APPLICATION_JSON);
  }

  /**
   * Deserializes HTTP response from JSON string.
   */
  protected <T> T deserializeObjectFromJson(HttpEntity entity, TypeReference<T> typeReference)
      throws IOException {
    return objectMapper.readValue(entity.getContent(), typeReference);
  }
}
