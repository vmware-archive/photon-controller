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

package com.vmware.photon.controller.deployer.configuration;

/**
 * This class represents a load balancer server configuration.
 */
public class LoadBalancerServer {
  private String serverName;
  private String serverAddress;

  public String getServerName() {
    return serverName;
  }

  public String getServerAddress() {
    return serverAddress;
  }

  public LoadBalancerServer(String serverName, String serverAddress) {
    this.serverName = serverName;
    this.serverAddress = serverAddress;

  }
}
