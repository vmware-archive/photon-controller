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

import com.vmware.photon.controller.nsx.gen.CreateLogicalRouterRequest;
import com.vmware.photon.controller.nsx.gen.CreateLogicalRouterResponse;
import com.vmware.photon.controller.nsx.gen.CreateTransportNodeRequest;
import com.vmware.photon.controller.nsx.gen.CreateTransportNodeResponse;
import com.vmware.photon.controller.nsx.gen.CreateTransportZoneRequest;
import com.vmware.photon.controller.nsx.gen.CreateTransportZoneResponse;
import com.vmware.photon.controller.nsx.gen.GetFabricNodeResponse;
import com.vmware.photon.controller.nsx.gen.GetLogicalRouterResponse;
import com.vmware.photon.controller.nsx.gen.GetTransportNodeResponse;
import com.vmware.photon.controller.nsx.gen.GetTransportZoneResponse;
import com.vmware.photon.controller.nsx.gen.RegisterFabricNodeRequest;
import com.vmware.photon.controller.nsx.gen.RegisterFabricNodeResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.IOException;

/**
 * A NSX client which implements RESTful calls to NSX API.
 */
public class NsxClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String basePath = "/api/v1";

  /**
   * Constructs a NSX client.
   */
  public NsxClient(String target, String username, String password) {
    this(target, username, password, null);
  }

  /**
   * Constructs a NSX client.
   */
  public NsxClient(String target, String username, String password, RestClient restClient) {
    this.restClient = restClient == null ? new RestClient(target, username, password) : restClient;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Registers a resource with NSX as a fabric node.
   */
  public RegisterFabricNodeResponse registerFabricNode(RegisterFabricNodeRequest request) throws IOException {
    final String path = basePath + "/fabric/nodes";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<RegisterFabricNodeResponse>() {
        });
  }

  /**
   * Registers a resource with NSX as a fabric node.
   */
  public void registerFabricNodeAsync(RegisterFabricNodeRequest request,
                                      FutureCallback<RegisterFabricNodeResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<RegisterFabricNodeResponse>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX fabric node.
   */
  public GetFabricNodeResponse getFabricNode(String nodeId) throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetFabricNodeResponse>() {
        });
  }

  /**
   * Gets a NSX fabric node.
   */
  public void getFabricNodeAsync(String nodeId,
                                 FutureCallback<GetFabricNodeResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetFabricNodeResponse>() {
        },
        responseCallback);
  }

  /**
   * Unregisters a NSX fabric node.
   */
  public void unregisterFabricNode(String nodeId) throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    delete(path, HttpStatus.SC_OK);
  }

  /**
   * Unregisters a NSX fabric node.
   */
  public void unregisterFabricNodeAsync(String nodeId,
                                        FutureCallback<Void> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    deleteAsync(path, HttpStatus.SC_OK, responseCallback);
  }

  /**
   * Creates a NSX transport node.
   */
  public CreateTransportNodeResponse createTransportNode(CreateTransportNodeRequest request) throws IOException {
    final String path = basePath + "/transport-nodes";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateTransportNodeResponse>() {
        });
  }

  /**
   * Creates a NSX transport node.
   */
  public void createTransportNodeAsync(CreateTransportNodeRequest request,
                                       FutureCallback<CreateTransportNodeResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateTransportNodeResponse>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX transport node.
   */
  public GetTransportNodeResponse getTransportNode(String id) throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportNodeResponse>() {
        });
  }

  /**
   * Deletes a NSX transport node.
   */
  public void deleteTransportNode(String id) throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    delete(path, HttpStatus.SC_OK);
  }

  /**
   * Deletes a NSX transport node.
   */
  public void deleteTransportNodeAsync(String id,
                                       FutureCallback<Void> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    deleteAsync(path, HttpStatus.SC_OK, responseCallback);
  }

  /**
   * Gets a NSX transport node.
   */
  public void getTransportNodeAsync(String id,
                                    FutureCallback<GetTransportNodeResponse> responseFutureCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportNodeResponse>() {
        },
        responseFutureCallback);
  }

  /**
   * Creates a NSX transport zone.
   */
  public CreateTransportZoneResponse createTransportZone(CreateTransportZoneRequest request) throws IOException {
    final String path = basePath + "/transport-zones";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateTransportZoneResponse>() {
        });
  }

  /**
   * Creates a NSX transport zone.
   */
  public void createTransportZoneAsync(CreateTransportZoneRequest request,
                                       FutureCallback<CreateTransportZoneResponse> responseCallback)
    throws IOException {
    final String path = basePath + "/transport-zones";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateTransportZoneResponse>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX transport zone.
   */
  public GetTransportZoneResponse getTransportZone(String id) throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportZoneResponse>() {
        });
  }

  /**
   * Gets a NSX transport zone.
   */
  public void getTransportZoneAsync(String id,
                                    FutureCallback<GetTransportZoneResponse> responseCallback)
    throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportZoneResponse>() {
        },
        responseCallback);
  }

  /**
   * Deletes a NSX transport zone.
   */
  public void deleteTransportZone(String id) throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    delete(path, HttpStatus.SC_OK);
  }

  /**
   * Deletes a NSX transport zone.
   */
  public void deleteTransportZoneAsync(String id,
                                       FutureCallback<Void> responseCallback)
    throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    deleteAsync(path, HttpStatus.SC_OK, responseCallback);
  }

  /**
   * Create a logical router.
   */
  public CreateLogicalRouterResponse createLogicalRouter(CreateLogicalRouterRequest request) throws IOException {
    final String path = basePath + "/logical-routers";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateLogicalRouterResponse>() {
        });
  }

  /**
   * Create a logical router.
   */
  public void createLogicalRouterAsync(CreateLogicalRouterRequest request,
                                       FutureCallback<CreateLogicalRouterResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/logical-routers";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<CreateLogicalRouterResponse>() {
        },
        responseCallback);
  }

  /**
   * Gets a logical router.
   */
  public GetLogicalRouterResponse getLogicalRouter(String id) throws IOException {
    final String path = basePath + "/logical-routers/" + id;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetLogicalRouterResponse>() {
        });
  }

  /**
   * Gets a logical router.
   */
  public void getLogicalRouterAsync(String id, FutureCallback<GetLogicalRouterResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/logical-routers/" + id;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetLogicalRouterResponse>() {
        },
        responseCallback);
  }

  /**
   * Deletes a logical router.
   */
  public void deleteLogicalRouter(String id) throws IOException {
    final String path = basePath + "/logical-routers/" + id;
    delete(path, HttpStatus.SC_OK);
  }

  /**
   * Deletes a logical router.
   */
  public void deleteLogicalRouterAsync(String id,
                                       FutureCallback<Void> responseCallback)
      throws IOException {
    final String path = basePath + "/logical-routers/" + id;
    deleteAsync(path, HttpStatus.SC_OK, responseCallback);
  }

  /**
   * Performs a POST HTTP request to NSX.
   */
  private <T> T post(final String path,
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
  private <T> void postAsync(final String path,
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
  private <T> T get(final String path,
                    final int expectedResponseStatus,
                    final TypeReference<T> typeReference) throws IOException {
    HttpResponse result = restClient.send(
        RestClient.Method.GET,
        path,
        null);

    restClient.check(result, expectedResponseStatus);
    T abc = deserializeObjectFromJson(result.getEntity(), typeReference);

    return abc;
  }

  /**
   * Performs a GET HTTP request to NSX.
   */
  private <T> void getAsync(final String path,
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
  private void delete(final String path,
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
  private void deleteAsync(final String path,
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

  /**
   * Serializes HTTP request to JSON string.
   */
  private StringEntity serializeObjectAsJson(Object o) throws JsonProcessingException {
    String payload = objectMapper.writeValueAsString(o);
    return new StringEntity(payload, ContentType.APPLICATION_JSON);
  }

  /**
   * Deserializes HTTP response from JSON string.
   */
  private <T> T deserializeObjectFromJson(HttpEntity entity, TypeReference<T> typeReference)
      throws IOException {
    return objectMapper.readValue(entity.getContent(), typeReference);
  }
}
