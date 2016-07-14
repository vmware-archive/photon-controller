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
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPortListResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * This is the class of the NSX Logical Router API implementation.
 */
public class LogicalRouterApi extends NsxClientApi {

  public static final String LOGICAL_ROUTERS_BASE_PATH = BASE_PATH + "/logical-routers";
  public static final String LOGICAL_ROUTER_PORTS_BASE_PATH = BASE_PATH + "/logical-router-ports";

  /**
   * Constructs a LogicalRouterApi object.
   */
  public LogicalRouterApi(RestClient restClient) {
    super(restClient);
  }

  /**
   * Creates a logical router.
   */
  public void createLogicalRouter(LogicalRouterCreateSpec spec,
                                  FutureCallback<LogicalRouter> responseCallback)
      throws IOException {
    postAsync(LOGICAL_ROUTERS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalRouter>() {},
        responseCallback
    );
  }

  /**
   * Gets a logical router.
   */
  public void getLogicalRouter(String id,
                               FutureCallback<LogicalRouter> responseCallback)
      throws IOException {
    getAsync(LOGICAL_ROUTERS_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<LogicalRouter>() {},
        responseCallback
    );
  }

  /**
   * Deletes a logical router.
   */
  public void deleteLogicalRouter(String id,
                                  FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(LOGICAL_ROUTERS_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Checks the existence of a logical router.
   */
  public void checkLogicalRouterExistence(String id,
                                          FutureCallback<Boolean> responseCallback)
      throws IOException {
    checkExistenceAsync(LOGICAL_ROUTERS_BASE_PATH + "/" + id,
        responseCallback);
  }

  /**
   * Creates a downlink port on tier-1 router to switch.
   */
  public void createLogicalRouterDownLinkPort(LogicalRouterDownLinkPortCreateSpec spec,
                                              FutureCallback<LogicalRouterDownLinkPort> responseCallback)
      throws IOException {
    postAsync(LOGICAL_ROUTER_PORTS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalRouterDownLinkPort>() {},
        responseCallback
    );
  }

  /**
   * Creates a port on tier0 router that connects to tier1 router.
   */
  public void createLogicalRouterLinkPortTier0(LogicalRouterLinkPortOnTier0CreateSpec spec,
                                               FutureCallback<LogicalRouterLinkPortOnTier0> responseCallback)
      throws IOException {
    postAsync(LOGICAL_ROUTER_PORTS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalRouterLinkPortOnTier0>() {},
        responseCallback
    );
  }

  /**
   * Creates a port on tier1 router that connects to tier0 router.
   */
  public void createLogicalRouterLinkPortTier1(LogicalRouterLinkPortOnTier1CreateSpec spec,
                                               FutureCallback<LogicalRouterLinkPortOnTier1> responseCallback)
      throws IOException {
    postAsync(LOGICAL_ROUTER_PORTS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalRouterLinkPortOnTier1>() {},
        responseCallback
    );
  }

  /**
   * Gets list of ports associated with the router.
   */
  public void listLogicalRouterPorts(String id,
                                     FutureCallback<LogicalRouterPortListResult> responseCallback)
      throws IOException {
    getAsync(LOGICAL_ROUTER_PORTS_BASE_PATH + "?logical_router_id=" + id,
        HttpStatus.SC_OK,
        new TypeReference<LogicalRouterPortListResult>() {},
        responseCallback
    );
  }

  /**
   * Deletes a router port.
   */
  public void deleteLogicalRouterPort(String id,
                                      FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(LOGICAL_ROUTER_PORTS_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Checks the existence of a logical router port.
   */
  public void checkLogicalRouterPortExistence(String id,
                                              FutureCallback<Boolean> responseCallback)
      throws IOException {
    checkExistenceAsync(LOGICAL_ROUTER_PORTS_BASE_PATH + "/" + id,
        responseCallback);
  }
}
