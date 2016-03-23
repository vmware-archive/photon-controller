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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;

import com.google.inject.BindingAnnotation;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

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
 * Root scheduler configuration.
 */
@SuppressWarnings("UnusedDeclaration")
public class Config {
  private String mode;

  private String constraintChecker;

  // Refresh interval for in-memory constraint checker cache in seconds.
  @NotNull
  @Range(min = 1, max = 600)
  private Integer refreshIntervalSec = 30;

  @NotNull
  @Range(min = 0, max = 65535)
  private Integer port = null;

  @NotNull
  @NotEmpty
  private String bind;

  @NotNull
  @NotEmpty
  private String registrationAddress;

  @NotNull
  @NotEmpty
  private String storagePath;

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  @Valid
  @NotNull
  private SchedulerConfig root = new SchedulerConfig();

  private PlaceParams rootPlaceParams;

  @Valid
  @NotNull
  public Config() {
    try {
      bind = InetAddress.getLocalHost().getHostAddress();
      registrationAddress = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  public String getMode() {
    return mode;
  }

  public String getConstraintChecker() {
    return constraintChecker;
  }

  public Integer getRefreshIntervalSec() {
    return refreshIntervalSec;
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

  public String getStoragePath() {
    return storagePath;
  }

  public LoggingConfiguration getLogging() {
    return logging;
  }

  public ZookeeperConfig getZookeeper() {
    return zookeeper;
  }

  public SchedulerConfig getRoot() {
    return root;
  }

  /**
   * Root scheduler port.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Port {
  }

  /**
   * Root scheduler bind address.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Bind {
  }

  /**
   * Root scheduler address to register in Zookeeper.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface RegistrationAddress {
  }

  /**
   * DCP storage path.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface StoragePath {
  }

  public void initRootPlaceParams() {
    rootPlaceParams = new PlaceParams();
    rootPlaceParams.setTimeout(root.getPlaceTimeoutMs());
    rootPlaceParams.setMinFanoutCount(root.getMinFanoutCount());
    rootPlaceParams.setMaxFanoutCount(root.getMaxFanoutCount());
  }

  public PlaceParams getRootPlaceParams() {
    return rootPlaceParams;
  }
}
