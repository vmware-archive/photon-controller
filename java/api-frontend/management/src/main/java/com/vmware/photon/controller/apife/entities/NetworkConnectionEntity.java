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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;

/**
 * NetworkConnection entity.
 */
public class NetworkConnectionEntity extends BaseEntity {

  public static final String KIND = "network-connection";

  private String network;

  private String ipAddress;

  private String netmask;

  public NetworkConnectionEntity() {
  }

  public NetworkConnectionEntity(String network, String ipAddress, String netmask) {
    this.network = network;
    this.ipAddress = ipAddress;
    this.netmask = netmask;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;

  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getNetmask() {
    return netmask;
  }

  public void setNetmask(String netmask) {
    this.netmask = netmask;
  }

  @Override
  public String getKind() {
    return KIND;
  }
}
