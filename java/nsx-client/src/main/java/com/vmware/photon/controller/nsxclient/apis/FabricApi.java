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
import com.vmware.photon.controller.nsxclient.models.CreateTransportNodeRequest;
import com.vmware.photon.controller.nsxclient.models.CreateTransportNodeResponse;
import com.vmware.photon.controller.nsxclient.models.CreateTransportZoneRequest;
import com.vmware.photon.controller.nsxclient.models.CreateTransportZoneResponse;
import com.vmware.photon.controller.nsxclient.models.GetFabricNodeResponse;
import com.vmware.photon.controller.nsxclient.models.GetFabricNodeStateResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportNodeResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportNodeStateResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportZoneResponse;
import com.vmware.photon.controller.nsxclient.models.GetTransportZoneSummaryResponse;
import com.vmware.photon.controller.nsxclient.models.RegisterFabricNodeRequest;
import com.vmware.photon.controller.nsxclient.models.RegisterFabricNodeResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * This is the class of the NSX Fabric API implementation.
 */
public class FabricApi extends NsxClientApi {

  /**
   * Constructs a FabricApi class.
   */
  public FabricApi(RestClient restClient) {
    super(restClient);
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
   * Gets the state of a NSX fabric node.
   */
  public GetFabricNodeStateResponse getFabricNodeState(String nodeId) throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId + "/state";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetFabricNodeStateResponse>() {
        });
  }

  /**
   * Gets the state of a NSX fabric node.
   */
  public void getFabricNodeStateAsync(String nodeId,
                                      FutureCallback<GetFabricNodeStateResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId + "/state";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetFabricNodeStateResponse>() {
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
   * Gets the state of a NSX transport node.
   */
  public GetTransportNodeStateResponse getTransportNodeState(String id) throws IOException {
    final String path = basePath + "/transport-nodes/" + id + "/state";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportNodeStateResponse>() {
        });
  }

  /**
   * Gets the state of a NSX transport node.
   */
  public void getTransportNodeStateAsync(String id,
                                         FutureCallback<GetTransportNodeStateResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes/" + id + "/state";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportNodeStateResponse>() {
        },
        responseCallback);
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
   * Gets the summary of a NSX transport zone.
   */
  public GetTransportZoneSummaryResponse getTransportZoneSummary(String id) throws IOException {
    final String path = basePath + "/transport-zones/" + id + "/summary";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportZoneSummaryResponse>() {
        });
  }

  /**
   * Gets the summary of a NSX transport zone.
   */
  public void getTransportZoneSummaryAsync(String id,
                                           FutureCallback<GetTransportZoneSummaryResponse> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-zones/" + id + "/summary";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<GetTransportZoneSummaryResponse>() {
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
}
