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
import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportZone;
import com.vmware.photon.controller.nsxclient.models.TransportZoneCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportZoneSummary;

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
  public FabricNode registerFabricNode(FabricNodeCreateSpec request) throws IOException {
    final String path = basePath + "/fabric/nodes";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<FabricNode>() {
        });
  }

  /**
   * Registers a resource with NSX as a fabric node.
   */
  public void registerFabricNodeAsync(FabricNodeCreateSpec request,
                                      FutureCallback<FabricNode> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<FabricNode>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX fabric node.
   */
  public FabricNode getFabricNode(String nodeId) throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<FabricNode>() {
        });
  }

  /**
   * Gets a NSX fabric node.
   */
  public void getFabricNodeAsync(String nodeId,
                                 FutureCallback<FabricNode> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<FabricNode>() {
        },
        responseCallback);
  }

  /**
   * Gets the state of a NSX fabric node.
   */
  public FabricNodeState getFabricNodeState(String nodeId) throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId + "/state";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<FabricNodeState>() {
        });
  }

  /**
   * Gets the state of a NSX fabric node.
   */
  public void getFabricNodeStateAsync(String nodeId,
                                      FutureCallback<FabricNodeState> responseCallback)
      throws IOException {
    final String path = basePath + "/fabric/nodes/" + nodeId + "/state";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<FabricNodeState>() {
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
  public TransportNode createTransportNode(TransportNodeCreateSpec request) throws IOException {
    final String path = basePath + "/transport-nodes";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportNode>() {
        });
  }

  /**
   * Creates a NSX transport node.
   */
  public void createTransportNodeAsync(TransportNodeCreateSpec request,
                                       FutureCallback<TransportNode> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportNode>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX transport node.
   */
  public TransportNode getTransportNode(String id) throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportNode>() {
        });
  }

  /**
   * Gets a NSX transport node.
   */
  public void getTransportNodeAsync(String id,
                                    FutureCallback<TransportNode> responseFutureCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes/" + id;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportNode>() {
        },
        responseFutureCallback);
  }

  /**
   * Gets the state of a NSX transport node.
   */
  public TransportNodeState getTransportNodeState(String id) throws IOException {
    final String path = basePath + "/transport-nodes/" + id + "/state";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportNodeState>() {
        });
  }

  /**
   * Gets the state of a NSX transport node.
   */
  public void getTransportNodeStateAsync(String id,
                                         FutureCallback<TransportNodeState> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-nodes/" + id + "/state";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportNodeState>() {
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
  public TransportZone createTransportZone(TransportZoneCreateSpec request) throws IOException {
    final String path = basePath + "/transport-zones";
    return post(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportZone>() {
        });
  }

  /**
   * Creates a NSX transport zone.
   */
  public void createTransportZoneAsync(TransportZoneCreateSpec request,
                                       FutureCallback<TransportZone> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-zones";
    postAsync(path,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportZone>() {
        },
        responseCallback);
  }

  /**
   * Gets a NSX transport zone.
   */
  public TransportZone getTransportZone(String id) throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportZone>() {
        });
  }

  /**
   * Gets a NSX transport zone.
   */
  public void getTransportZoneAsync(String id,
                                    FutureCallback<TransportZone> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-zones/" + id;
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportZone>() {
        },
        responseCallback);
  }

  /**
   * Gets the summary of a NSX transport zone.
   */
  public TransportZoneSummary getTransportZoneSummary(String id) throws IOException {
    final String path = basePath + "/transport-zones/" + id + "/summary";
    return get(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportZoneSummary>() {
        });
  }

  /**
   * Gets the summary of a NSX transport zone.
   */
  public void getTransportZoneSummaryAsync(String id,
                                           FutureCallback<TransportZoneSummary> responseCallback)
      throws IOException {
    final String path = basePath + "/transport-zones/" + id + "/summary";
    getAsync(path,
        HttpStatus.SC_OK,
        new TypeReference<TransportZoneSummary>() {
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
