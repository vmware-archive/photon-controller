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
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import com.google.inject.BindingAnnotation;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.InetAddress;
import java.net.UnknownHostException;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This class implements configuration state for the bare-metal-provisioner service.
 */
public class ProvisionerConfig {

  @NotNull
  @Range(min = 0, max = 65535)
  private Integer port;

  @NotBlank
  private String bind;

  @NotBlank
  private String registrationAddress;

  @NotBlank
  private String storagePath;

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  private Boolean usePhotonDHCP;

  private Integer slingshotLogVerbosity;

  public ProvisionerConfig() {
    try {
      bind = InetAddress.getLocalHost().getHostAddress();
      registrationAddress = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  public Integer getPort() {
    return port;
  }

  public String getBind() {
    return bind;
  }

  public String getRegistrationAddress() {
    return registrationAddress;
  }

  public LoggingConfiguration getLogging() {
    return checkNotNull(logging);
  }

  public String getStoragePath() {
    return storagePath;
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

  /**
   * Bare metal provisioner Xenon port.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Port {
  }

  /**
   * Bare metal provisioner bind address.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Bind {
  }

  /**
   * Bare metal provisioner address for zookeeper.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface RegistrationAddress {
  }

  /**
   * Xenon storage path.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface StoragePath {
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
