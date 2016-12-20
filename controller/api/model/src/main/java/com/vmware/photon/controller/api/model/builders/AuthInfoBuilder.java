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

package com.vmware.photon.controller.api.model.builders;

import com.vmware.photon.controller.api.model.AuthInfo;

import java.util.List;

/**
 * This class implements a builder for {@link AuthInfo} object.
 */
public class AuthInfoBuilder {

  private boolean enabled;

  private String endpoint;

  private Integer port;

  private String tenant;

  private String username;

  private String password;

  private List<String> securityGroups;

  public AuthInfoBuilder() {
    this.enabled = false;
  }

  public AuthInfoBuilder enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public AuthInfoBuilder endpoint(String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public AuthInfoBuilder port(Integer port) {
    this.port = port;
    return this;
  }

  public AuthInfoBuilder tenant(String tenant) {
    this.tenant = tenant;
    return this;
  }

  public AuthInfoBuilder username(String username) {
    this.username = username;
    return this;
  }

  public AuthInfoBuilder password(String password) {
    this.password = password;
    return this;
  }

  public AuthInfoBuilder securityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
    return this;
  }

  public AuthInfo build() {
    AuthInfo authInfo = new AuthInfo();
    authInfo.setEnabled(this.enabled);
    authInfo.setEndpoint(this.endpoint);
    authInfo.setPort(this.port);
    authInfo.setTenant(this.tenant);
    authInfo.setUsername(this.username);
    authInfo.setPassword(this.password);
    authInfo.setSecurityGroups(this.securityGroups);
    return authInfo;
  }
}
