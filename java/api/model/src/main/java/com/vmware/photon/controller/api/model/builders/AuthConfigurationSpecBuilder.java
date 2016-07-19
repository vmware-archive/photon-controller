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

import com.vmware.photon.controller.api.model.AuthConfigurationSpec;

import java.util.List;

/**
 * This class implemets a builder for {@link AuthConfigurationSpec} object.
 */
public class AuthConfigurationSpecBuilder {

  private boolean enabled;

  private String tenant;

  private String password;

  private List<String> securityGroups;

  public AuthConfigurationSpecBuilder() {
    this.enabled = false;
  }

  public AuthConfigurationSpecBuilder enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public AuthConfigurationSpecBuilder tenant(String tenant) {
    this.tenant = tenant;
    return this;
  }

  public AuthConfigurationSpecBuilder password(String password) {
    this.password = password;
    return this;
  }

  public AuthConfigurationSpecBuilder securityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
    return this;
  }

  public AuthConfigurationSpec build() {
    AuthConfigurationSpec authConfigSpec = new AuthConfigurationSpec();
    authConfigSpec.setEnabled(this.enabled);
    authConfigSpec.setTenant(this.tenant);
    authConfigSpec.setPassword(this.password);
    authConfigSpec.setSecurityGroups(this.securityGroups);
    return authConfigSpec;
  }
}
