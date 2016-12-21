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

package com.vmware.photon.controller.core;

import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * This class implements configuration state for the photon-controller-core service.
 */
// This annotation is added to ignore api related configuration sections.
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotonControllerConfig {

  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  private SchedulerConfig root = new SchedulerConfig();

  @Valid
  @NotNull
  @JsonProperty("deployer")
  private DeployerConfig deployerConfig;

  @Valid
  @NotNull
  @JsonProperty("photon_controller_logging")
  private LoggingConfiguration logging = new LoggingConfiguration();

  @Valid
  @NotNull
  private AuthConfig auth;

  public XenonConfig getXenonConfig() {
    return this.xenonConfig;
  }

  public SchedulerConfig getRoot() {
    return root;
  }

  public DeployerConfig getDeployerConfig() {
    return this.deployerConfig;
  }

  public LoggingConfiguration getLogging() {
    return checkNotNull(logging);
  }

  public AuthConfig getAuth() {
    return auth;
  }

  public void setAuth(AuthConfig auth) {
    this.auth = auth;
  }
}
