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

package com.vmware.photon.controller.common.xenon.host;

import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

/**
 * This class implements basic configuration state for a Xenon host.
 */
public class XenonConfig {

  @Range(min = 0, max = 65535)
  private Integer port;

  @NotBlank
  private String bindAddress;

  @NotBlank
  private String registrationAddress;

  @NotBlank
  private String storagePath;

  @NotEmpty
  private String[] peerNodes;

  public void setPort(int port) {
    this.port = port;
  }

  public Integer getPort() {
    return this.port;
  }

  public void setBindAddress(String bindAddress) {
    this.bindAddress = bindAddress;
  }

  public String getBindAddress() {
    return this.bindAddress;
  }

  public void setRegistrationAddress(String registrationAddress) {
    this.registrationAddress = registrationAddress;
  }

  public String getRegistrationAddress() {
    return this.registrationAddress;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public String getStoragePath() {
    return this.storagePath;
  }

  public void setPeerNodes(String[] peerNodes) {
    this.peerNodes = peerNodes;
  }

  public String[] getPeerNodes() {
    return this.peerNodes;
  }
}
