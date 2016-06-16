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
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalPortListResult;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * Class for NSX logical switch related APIs.
 */
public class LogicalSwitchApi extends NsxClientApi {
  public final String logicalSwitchBasePath = basePath + "/logical-switches";
  public final String logicalPortBasePath = basePath + "/logical-ports";

  public LogicalSwitchApi(RestClient restClient) {
    super(restClient);
  }

  public void createLogicalSwitch(LogicalSwitchCreateSpec spec,
                                  FutureCallback<LogicalSwitch> responseCallback)
      throws IOException {

    postAsync(logicalSwitchBasePath,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalSwitch>() {
        },
        responseCallback
    );
  }

  public void getLogicalSwitchState(String id,
                                    FutureCallback<LogicalSwitchState> responseCallback)
      throws Exception {

    getAsync(logicalSwitchBasePath + "/" + id + "/state",
        HttpStatus.SC_OK,
        new TypeReference<LogicalSwitchState>() {
        },
        responseCallback
    );
  }

  /**
   * Get list of ports.
   * @param responseCallback
   */
  public void listLogicalSwitchPorts(FutureCallback<LogicalPortListResult> responseCallback)
      throws IOException {
    getAsync(logicalPortBasePath,
        HttpStatus.SC_OK,
        new TypeReference<LogicalPortListResult>() {},
        responseCallback
    );
  }

  public void deleteLogicalSwitch(String id,
                                  FutureCallback<Void> responseCallback) throws Exception {

    deleteAsync(logicalSwitchBasePath + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  public void createLogicalPort(LogicalPortCreateSpec spec,
                                FutureCallback<LogicalPort> responseCallback)
    throws IOException {

    postAsync(logicalPortBasePath,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalPort>() {
        },
        responseCallback
    );
  }

  public void deleteLogicalPort(String id, FutureCallback<Void> responseCallback) throws Exception {

    deleteAsync(logicalPortBasePath + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Check the existence of a logical switch port.
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public void checkLogicalSwitchPortExistence(String id, FutureCallback<Boolean> responseCallback) throws IOException {
    checkExistenceAsync(logicalPortBasePath + "/" + id, responseCallback);
  }
}
