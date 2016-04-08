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
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.HttpStatus;

/**
 * Class for NSX logical switch related APIs.
 */
public class LogicalSwitchApi extends NsxClientApi {
  public final String logicalSwitchBasePath = basePath + "/logical-switches";

  public LogicalSwitchApi(RestClient restClient) {
    super(restClient);
  }

  public LogicalSwitch createLogicalSwitch(LogicalSwitchCreateSpec spec) throws Exception {
    return post(logicalSwitchBasePath,
        serializeObjectAsJson(spec),
        HttpStatus.SC_CREATED,
        new TypeReference<LogicalSwitch>() {}
    );
  }

  public LogicalSwitchState getLogicalSwitchState(String id) throws Exception {
    return get(logicalSwitchBasePath + "/" + id + "/state",
        HttpStatus.SC_OK,
        new TypeReference<LogicalSwitchState>() {}
    );
  }

  public void deleteLogicalSwitch(String id) throws Exception {
    delete(logicalSwitchBasePath + "/" + id, HttpStatus.SC_OK);
  }
}
