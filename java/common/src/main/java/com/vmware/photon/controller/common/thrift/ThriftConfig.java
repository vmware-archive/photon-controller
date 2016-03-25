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

package com.vmware.photon.controller.common.thrift;

import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.Range;

/**
 * This class implements basic configuration state for a Thrift service.
 */
public class ThriftConfig {

  @Range(min = 0, max = 65535)
  private Integer port;

  @NotBlank
  private String bindAddress;

  @NotBlank
  private String registrationAddress;

  public Integer getPort() {
    return this.port;
  }

  public String getBindAddress() {
    return this.bindAddress;
  }

  public String getRegistrationAddress() {
    return this.registrationAddress;
  }
}
