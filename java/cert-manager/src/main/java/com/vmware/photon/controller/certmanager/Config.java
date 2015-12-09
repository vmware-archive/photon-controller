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
package com.vmware.photon.controller.certmanager;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;

import com.google.inject.BindingAnnotation;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Cert manager configuration.
 */
public class Config {

  @NotNull
  @Range(min = 0, max = 65535)
  private Integer port = null;

  @NotNull
  @NotEmpty
  private String bind;

  private LoggingConfiguration logging = new LoggingConfiguration();

  public Integer getPort() {
    return port;
  }

  public String getBind() {
    return bind;
  }

  public LoggingConfiguration getLogging() {
    return logging;
  }

  /**
   * CertManager port.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Port {
  }

  /**
   * CertManager bind address.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Bind {
  }
}
