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

  public static final String FABRIC_NODES_BASE_PATH = BASE_PATH + "/fabric/nodes";
  public static final String TRANSPORT_NODES_BASE_PATH = BASE_PATH + "/transport-nodes";
  public static final String TRANSPORT_ZONES_BASE_PATH = BASE_PATH + "/transport-zones";

  /**
   * Constructs a FabricApi object.
   */
  public FabricApi(RestClient restClient) {
    super(restClient);
  }

  /**
   * Registers a resource with NSX as a fabric node.
   */
  public void registerFabricNode(FabricNodeCreateSpec request,
                                 FutureCallback<FabricNode> responseCallback)
      throws IOException {
    postAsync(FABRIC_NODES_BASE_PATH,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<FabricNode>() {},
        responseCallback);
  }

  /**
   * Gets a NSX fabric node.
   */
  public void getFabricNode(String id,
                            FutureCallback<FabricNode> responseCallback)
      throws IOException {
    getAsync(FABRIC_NODES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<FabricNode>() {},
        responseCallback);
  }

  /**
   * Gets the state of a NSX fabric node.
   */
  public void getFabricNodeState(String id,
                                 FutureCallback<FabricNodeState> responseCallback)
      throws IOException {
    getAsync(FABRIC_NODES_BASE_PATH + "/" + id + "/state",
        HttpStatus.SC_OK,
        new TypeReference<FabricNodeState>() {},
        responseCallback);
  }

  /**
   * Unregisters a NSX fabric node.
   */
  public void unregisterFabricNode(String id,
                                   FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(FABRIC_NODES_BASE_PATH + "/" + id + "?unprepare_host=true",
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Creates a NSX transport node.
   */
  public void createTransportNode(TransportNodeCreateSpec request,
                                  FutureCallback<TransportNode> responseCallback)
      throws IOException {
    postAsync(TRANSPORT_NODES_BASE_PATH,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportNode>() {},
        responseCallback);
  }

  /**
   * Gets a NSX transport node.
   */
  public void getTransportNode(String id,
                               FutureCallback<TransportNode> responseFutureCallback)
      throws IOException {
    getAsync(TRANSPORT_NODES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<TransportNode>() {},
        responseFutureCallback);
  }

  /**
   * Gets the state of a NSX transport node.
   */
  public void getTransportNodeState(String id,
                                    FutureCallback<TransportNodeState> responseCallback)
      throws IOException {
    getAsync(TRANSPORT_NODES_BASE_PATH + "/" + id + "/state",
        HttpStatus.SC_OK,
        new TypeReference<TransportNodeState>() {},
        responseCallback);
  }

  /**
   * Deletes a NSX transport node.
   */
  public void deleteTransportNode(String id,
                                  FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(TRANSPORT_NODES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Creates a NSX transport zone.
   */
  public void createTransportZone(TransportZoneCreateSpec request,
                                  FutureCallback<TransportZone> responseCallback)
      throws IOException {
    postAsync(TRANSPORT_ZONES_BASE_PATH,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<TransportZone>() {},
        responseCallback);
  }

  /**
   * Gets a NSX transport zone.
   */
  public void getTransportZone(String id,
                               FutureCallback<TransportZone> responseCallback)
      throws IOException {
    getAsync(TRANSPORT_ZONES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<TransportZone>() {},
        responseCallback);
  }

  /**
   * Gets the summary of a NSX transport zone.
   */
  public void getTransportZoneSummary(String id,
                                      FutureCallback<TransportZoneSummary> responseCallback)
      throws IOException {
    getAsync(TRANSPORT_ZONES_BASE_PATH + "/" + id + "/summary",
        HttpStatus.SC_OK,
        new TypeReference<TransportZoneSummary>() {},
        responseCallback);
  }

  /**
   * Deletes a NSX transport zone.
   */
  public void deleteTransportZone(String id,
                                  FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(TRANSPORT_ZONES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }
}
