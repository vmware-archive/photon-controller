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
 * This is the class of the NSX Logical Switch API implementation.
 */
public class LogicalSwitchApi extends NsxClientApi {

  public static final String LOGICAL_SWITCHS_BASE_PATH = BASE_PATH + "/logical-switches";
  public static final String LOGICAL_PORTS_BASE_PATH = BASE_PATH + "/logical-ports";

  /**
   * Constructs a LogicalSwitchApi object.
   */
  public LogicalSwitchApi(RestClient restClient) {
    super(restClient);
  }

  /**
   * Creates a logical switch.
   */
  public void createLogicalSwitch(LogicalSwitchCreateSpec spec,
                                  FutureCallback<LogicalSwitch> responseCallback)
      throws IOException {
    postAsync(LOGICAL_SWITCHS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalSwitch>() {
        },
        responseCallback
    );
  }

  /**
   * Gets the state of a logical switch.
   */
  public void getLogicalSwitchState(String id,
                                    FutureCallback<LogicalSwitchState> responseCallback)
      throws IOException {
    getAsync(LOGICAL_SWITCHS_BASE_PATH + "/" + id + "/state",
        HttpStatus.SC_OK,
        new TypeReference<LogicalSwitchState>() {
        },
        responseCallback
    );
  }

  /**
   * Gets list of ports associated with the switch.
   */
  public void listLogicalSwitchPorts(FutureCallback<LogicalPortListResult> responseCallback)
      throws IOException {
    getAsync(LOGICAL_PORTS_BASE_PATH,
        HttpStatus.SC_OK,
        new TypeReference<LogicalPortListResult>() {
        },
        responseCallback
    );
  }

  /**
   * Deletes a logical switch.
   */
  public void deleteLogicalSwitch(String id,
                                  FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(LOGICAL_SWITCHS_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Checks the existence of a logical switch.
   */
  public void checkLogicalSwitchExistence(String id,
                                          FutureCallback<Boolean> responseCallback)
      throws IOException {
    checkExistenceAsync(LOGICAL_SWITCHS_BASE_PATH + "/" + id,
        responseCallback);
  }

  /**
   * Creates a port on the switch.
   */
  public void createLogicalPort(LogicalPortCreateSpec spec,
                                FutureCallback<LogicalPort> responseCallback)
    throws IOException {
    postAsync(LOGICAL_PORTS_BASE_PATH,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalPort>() {
        },
        responseCallback
    );
  }

  /**
   * Deletes a port on the switch.
   */
  public void deleteLogicalPort(String id,
                                FutureCallback<Void> responseCallback)
      throws IOException {
    deleteLogicalPort(id,
        responseCallback,
        false);
  }

  /**
   * Deletes a port on the switch.
   */
  public void deleteLogicalPort(String id,
                                FutureCallback<Void> responseCallback,
                                boolean forceDetach)
      throws IOException {
    String url = LOGICAL_PORTS_BASE_PATH + "/" + id;
    if (forceDetach) {
      url += "?detach=true";
    }

    deleteAsync(url,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Check the existence of a logical switch port.
   */
  public void checkLogicalSwitchPortExistence(String id,
                                              FutureCallback<Boolean> responseCallback)
      throws IOException {
    checkExistenceAsync(LOGICAL_PORTS_BASE_PATH + "/" + id,
        responseCallback);
  }
}
