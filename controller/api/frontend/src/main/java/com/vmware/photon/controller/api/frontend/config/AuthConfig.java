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

package com.vmware.photon.controller.api.frontend.config;

import java.util.List;

/**
 * Authentication configuration.
 */
public class AuthConfig {

  private boolean enableAuth;

  private String sharedSecret;


  private String authDomain;

  /**
   * This value represents the auth server address.
   */
  private String authServerAddress;

  /**
   * This value represents the auth server's load balancer address.
   */
  private String authLoadBalancerAddress;

  /**
   * This value represents the OAuth server port.
   */
  private Integer authServerPort;

  /**
   * LightWave user name.
   */
  private String authUserName;

  /**
   * Password for the given LightWave user.
   */
  private String authPassword;

  /**
   * Endpoint to the oAuth login service for Swagger.
   */
  private String authSwaggerLoginEndpoint;

  /**
   * Endpoint to the oAuth logout service for Swagger.
   */
  private String authSwaggerLogoutEndpoint;

  /**
   * Endpoint to the oAuth login service for Mgmt UI.
   */
  private String authMgmtUiLoginEndpoint;

  /**
   * Endpoint to the oAuth logout service for Mgmt UI.
   */
  private String authMgmtUiLogoutEndpoint;

  /**
   * Security groups.
   */
  private List<String> authSecurityGroups;

  public boolean isAuthEnabled() {
    return this.enableAuth;
  }

  public String getAuthServerAddress() {
    return this.authServerAddress;
  }

  public Integer getAuthServerPort() {
    return this.authServerPort;
  }

  public String getAuthDomain() {
    return this.authDomain;
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

  public void setAuthServerPort(Integer authServerPort) {
    this.authServerPort = authServerPort;
  }

  public void setAuthDomain(String tenant) {
    this.authDomain = tenant;
  }

  public String getAuthUserName() {
    return authUserName;
  }

  public void setAuthUserName(String authUserName) {
    this.authUserName = authUserName;
  }

  public String getAuthPassword() {
    return authPassword;
  }

  public void setAuthPassword(String authPassword) {
    this.authPassword = authPassword;
  }

  public String getAuthSwaggerLoginEndpoint() {
    return authSwaggerLoginEndpoint;
  }

  public void setAuthSwaggerLoginEndpoint(String authSwaggerLoginEndpoint) {
    this.authSwaggerLoginEndpoint = authSwaggerLoginEndpoint;
  }

  public String getAuthSwaggerLogoutEndpoint() {
    return authSwaggerLogoutEndpoint;
  }

  public void setAuthSwaggerLogoutEndpoint(String authSwaggerLogoutEndpoint) {
    this.authSwaggerLogoutEndpoint = authSwaggerLogoutEndpoint;
  }

  public String getAuthMgmtUiLoginEndpoint() {
    return authMgmtUiLoginEndpoint;
  }

  public void setAuthMgmtUiLoginEndpoint(String authMgmtUiLoginEndpoint) {
    this.authMgmtUiLoginEndpoint = authMgmtUiLoginEndpoint;
  }

  public String getAuthMgmtUiLogoutEndpoint() {
    return authMgmtUiLogoutEndpoint;
  }

  public void setAuthMgmtUiLogoutEndpoint(String authMgmtUiLogoutEndpoint) {
    this.authMgmtUiLogoutEndpoint = authMgmtUiLogoutEndpoint;
  }

  public List<String> getAuthSecurityGroups() {
    return authSecurityGroups;
  }

  public void setAuthSecurityGroups(List<String> authSecurityGroups) {
    this.authSecurityGroups = authSecurityGroups;
  }

  public String getAuthLoadBalancerAddress() {
    return authLoadBalancerAddress;
  }

  public void setAuthLoadBalancerAddress(String authLoadBalancerAddress) {
    this.authLoadBalancerAddress = authLoadBalancerAddress;
  }
}
