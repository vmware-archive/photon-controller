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

import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for Apis.
 */
public abstract class ApiBase {
  protected static ObjectMapper objectMapper = new ObjectMapper();
  protected final RestClient restClient;

  public ApiBase(RestClient restClient) {
    this.restClient = restClient;
  }

  public abstract String getBasePath();

  public static StringEntity serializeObjectAsJson(Object o) throws JsonProcessingException {
    String payload = objectMapper.writeValueAsString(o);

    return new StringEntity(payload, ContentType.APPLICATION_JSON);
  }

  public static <T> T deserializeObjectFromJson(String jsonString, TypeReference<T> typeReference)
      throws IOException {
    return objectMapper.readValue(jsonString, typeReference);
  }

  public final Task parseTaskFromHttpResponse(HttpResponse httpResponse) throws IOException {
    return this.restClient.parseHttpResponse(httpResponse, new TypeReference<Task>() {
    });
  }

  /**
   * Creates an object as async.
   *
   * @param path
   * @param responseCallback
   * @throws IOException
   */
  public final void createObjectAsync(final String path, final HttpEntity payload, final FutureCallback<Task>
      responseCallback)
      throws IOException {

    this.restClient.performAsync(
        RestClient.Method.POST,
        path,
        payload,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            Task task = null;
            try {
              restClient.checkResponse(result, HttpStatus.SC_CREATED);
              task = restClient.parseHttpResponse(result, new TypeReference<Task>() {
              });
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            if (task != null) {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("createObjectAsync returned task [%s] %s", this
                  .toString(), path));
              responseCallback.onSuccess(task);
            } else {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("createObjectAsync failed [%s] %s", this
                  .toString(), path));
            }
          }

          @Override
          public void failed(Exception ex) {
              Logger.getAnonymousLogger().log(Level.INFO, "{}", ex);
              responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("createAsync %s was cancelled",
                path)));
          }
        }
    );
  }

  /**
   * Deletes an object as async.
   *
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public final void deleteObjectAsync(final String id, final FutureCallback<Task> responseCallback) throws
      IOException {
    final String path = String.format("%s/%s", getBasePath(), id);

    this.restClient.performAsync(
        RestClient.Method.DELETE,
        path,
        null,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            Task task = null;
            try {
              restClient.checkResponse(result, HttpStatus.SC_CREATED);
              task = restClient.parseHttpResponse(result, new TypeReference<Task>() {
              });
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            if (task != null) {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("deleteObjectAsync returned task [%s] %s", this
                  .toString(), path));
              responseCallback.onSuccess(task);
            } else {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("deleteObjectAsync failed [%s] %s", this
                  .toString(), path));
            }
          }

          @Override
          public void failed(Exception ex) {
              Logger.getAnonymousLogger().log(Level.INFO, "{}", ex);
              responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("deleteAsync %s was cancelled", path)));
          }
        }
    );
  }

  public final <T> void getObjectByPathAsync(
      final String path,
      final FutureCallback<T> responseCallback,
      final TypeReference<T> tr) throws IOException {
    getObjectByPathAsync(path, HttpStatus.SC_OK, responseCallback, tr);
  }

  public final <T> void getObjectByPathAsync(
      final String path,
      final int expectedStatus,
      final FutureCallback<T> responseCallback,
      final TypeReference<T> tr) throws IOException {
    this.restClient.performAsync(
        RestClient.Method.GET,
        path,
        null,
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            T response = null;
            try {
              restClient.checkResponse(result, expectedStatus);
              response = restClient.parseHttpResponse(result, tr);
            } catch (Throwable e) {
              responseCallback.onFailure(e);
            }

            if (response != null) {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("getObjectByPathAsync returned task [%s] %s",
                  this.toString(), path));
              responseCallback.onSuccess(response);
            } else {
              Logger.getAnonymousLogger().log(Level.INFO, String.format("getObjectByPathAsync failed [%s] %s",
                  this.toString(), path));
            }
          }

          @Override
          public void failed(Exception ex) {
              Logger.getAnonymousLogger().log(Level.INFO, "{}", ex);
              responseCallback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            responseCallback.onFailure(new RuntimeException(String.format("getObjectByPathAsync %s was cancelled",
                path)));
          }
        }
    );
  }

  /**
   * Generates query string from query params.
   *
   * @param queryParams
   * @return
   * @throws UnsupportedEncodingException
   */
  public String generateQueryString(Map<String, String> queryParams) throws UnsupportedEncodingException {
    StringBuilder query = new StringBuilder();
    if (queryParams == null) {
      return query.toString();
    }
    if (queryParams != null && queryParams.size() > 0) {
      query.append("?");
    }
    for (Entry<String, String> param : queryParams.entrySet()) {
      query.append(String.format("%s=%s&", URLEncoder.encode(param.getKey(), "UTF-8"),
          URLEncoder.encode(param.getValue(), "UTF-8")));
    }
    return query.deleteCharAt(query.length() - 1).toString();
  }
}
