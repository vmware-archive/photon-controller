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

package com.vmware.photon.controller.apife.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication configuration.
 */
public class AuthConfig {

  @JsonProperty("enable_auth")
  private boolean enableAuth;

  @JsonProperty("auth_server_address")
  private String authServerAddress;

  @JsonProperty
  private String sharedSecret;

  @JsonProperty("auth_server_port")
  private int authServerPort;

  @JsonProperty("tenant")
  private String tenant;

  public boolean isAuthEnabled() {
    return this.enableAuth;
  }

  public String getAuthServerAddress() {
    return this.authServerAddress;
  }

  public int getAuthServerPort() {
    return this.authServerPort;
  }

  public String getTenant() {
    return this.tenant;
  }

  public void setAuthServerAddress(String url) {
    this.authServerAddress = url;
  }

  public void setEnableAuth(boolean enableAuth) {
    this.enableAuth = enableAuth;
  }

  public String getSharedSecret() {
    return sharedSecret;
  }

  public void setSharedSecret(String sharedSecret) {
    this.sharedSecret = sharedSecret;
  }

  public void setAuthServerPort(int authServerPort) {
    this.authServerPort = authServerPort;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }
}
