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

package com.vmware.photon.controller.chairman;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

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
 * Chairman configuration.
 */
public class Config {

  @NotNull
  @Range(min = 0, max = 65535)
  private Integer port = null;

  @NotNull
  @NotEmpty
  private String bind;

  @NotNull
  @NotEmpty
  private String registrationAddress;

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  public Config() {
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
    return logging;
  }

  public ZookeeperConfig getZookeeper() {
    return zookeeper;
  }

  /**
   * Chairman port.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Port {
  }

  /**
   * Chairman bind address.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Bind {
  }

  /**
   * Chairman address to be registered in Zookeeper.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface RegistrationAddress{
  }
}
