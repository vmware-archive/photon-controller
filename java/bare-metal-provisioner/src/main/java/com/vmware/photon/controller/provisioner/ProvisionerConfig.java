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

package com.vmware.photon.controller.provisioner;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.BindingAnnotation;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This class implements configuration state for the bare-metal-provisioner service.
 */
public class ProvisionerConfig {

  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  @NotNull
  private String slingshotLogDirectory;

  public XenonConfig getXenonConfig() {
    return checkNotNull(this.xenonConfig);
  }

  private Boolean usePhotonDHCP;

  private Integer slingshotLogVerbosity;

  public LoggingConfiguration getLogging() {
    return checkNotNull(logging);
  }

  public ZookeeperConfig getZookeeper() {
    return zookeeper;
  }

  public Boolean getUsePhotonDHCP() {
    if (usePhotonDHCP == null) {
      return Boolean.FALSE;
    } else {
      return usePhotonDHCP;
    }
  }

  public Integer getSlingshotLogVerbosity() {
    return slingshotLogVerbosity;
  }

  public String getSlingshotLogDirectory() {
    return slingshotLogDirectory;
  }


  /**
   * Bare metal provisioner is not enabled if DHCP is not enabled.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface UsePhotonDHCP {
  }

  /**
   * Bare metal provisioner is not enabled if DHCP is not enabled.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface SlingshotLogVerbosity {
  }
}
