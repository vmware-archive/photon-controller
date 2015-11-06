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

package com.vmware.photon.controller.auth;

/**
 * Represents client registration arguments.
 */
public class ClientRegistrationArguments {
  private String authServerAddress;
  private int authServerPort;
  private String tenant;
  private String username;
  private String password;
  private String loginRedirectEndpoint;
  private String logoutRedirectEndpoint;

  public ClientRegistrationArguments(String authServerAddress, int authServerPort, String tenant, String username,
                                     String password, String loginRedirectEndpoint, String logoutRedirectEndpoint) {

    this.authServerAddress = authServerAddress;
    this.authServerPort = authServerPort;
    this.tenant = tenant;
    this.username = username;
    this.password = password;
    this.loginRedirectEndpoint = loginRedirectEndpoint;
    this.logoutRedirectEndpoint = logoutRedirectEndpoint;
  }

  public String getAuthServerAddress() {
    return authServerAddress;
  }

  public int getAuthServerPort() {
    return authServerPort;
  }

  public String getTenant() {
    return tenant;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getLoginRedirectEndpoint() {
    return loginRedirectEndpoint;
  }

  public String getLogoutRedirectEndpoint() {
    return logoutRedirectEndpoint;
  }
}
