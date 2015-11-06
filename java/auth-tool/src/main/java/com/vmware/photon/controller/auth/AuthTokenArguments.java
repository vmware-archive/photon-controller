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
 * Class that represents authToken tool commandline Arguments.
 */
public class AuthTokenArguments {
  private String authServerAddress;
  private int authServerPort;
  private String tenant;
  private String username;
  private String password;
  private String refreshToken;

  /**
   * Auth token arguments by username/password.
   *
   * @param authServerAddress
   * @param authServerPort
   * @param tenant
   * @param username
   * @param password
   */
  public AuthTokenArguments(String authServerAddress, int authServerPort, String tenant, String username,
                            String password) {
    this.authServerAddress = authServerAddress;
    this.authServerPort = authServerPort;
    this.tenant = tenant;
    this.username = username;
    this.password = password;
  }

  /**
   * Auth token arguments by refresh token.
   *
   * @param authServerAddress
   * @param authServerPort
   * @param refreshToken
   */
  public AuthTokenArguments(String authServerAddress, int authServerPort, String tenant, String refreshToken) {
    this.authServerAddress = authServerAddress;
    this.authServerPort = authServerPort;
    this.tenant = tenant;
    this.refreshToken = refreshToken;
  }

  /**
   * Gets the auth server FQDN.
   */
  public String getAuthServerAddress() {
    return this.authServerAddress;
  }

  /**
   * Gets the auth server port.
   */
  public int getAuthServerPort() {
    return this.authServerPort;
  }

  /**
   * Gets the username.
   */
  public String getUsername() {
    return this.username;
  }

  /**
   * Gets the password.
   */
  public String getPassword() {
    return this.password;
  }

  /**
   * Gets the refresh token.
   * @return
   */
  public String getRefreshToken() {
    return this.refreshToken;
  }

  /**
   * Gets the tenant.
   * @return
   */
  public String getTenant() {
    return this.tenant;
  }
}
