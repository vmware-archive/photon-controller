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

package com.vmware.photon.controller.nsxclient;

import com.vmware.photon.controller.nsxclient.apis.FabricApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;

/**
 * This class represents the NSX client.
 */
public class NsxClient {

  private final RestClient restClient;

  private final FabricApi fabricApi;
  private final LogicalSwitchApi logicalSwitchApi;

  public NsxClient(String target,
                   String username,
                   String password) {
    if (!target.startsWith("https")) {
      target = "https://" + target;
    }

    this.restClient = new RestClient(target, username, password);

    this.fabricApi = new FabricApi(restClient);
    this.logicalSwitchApi = new LogicalSwitchApi(restClient);
  }

  public FabricApi getFabricApi() {
    return this.fabricApi;
  }

  public LogicalSwitchApi getLogicalSwitchApi() {
    return this.logicalSwitchApi;
  }
}
