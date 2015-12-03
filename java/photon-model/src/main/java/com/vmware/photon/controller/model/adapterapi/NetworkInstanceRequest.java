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

package com.vmware.photon.controller.model.adapterapi;

import java.net.URI;

/**
 * Request to create/destroy a network instance on a given compute.
 */
public class NetworkInstanceRequest {

  /**
   * Type of an Instance Request.
   */
  public enum InstanceRequestType {
    CREATE, DELETE
  }

  /**
   * Destroy or create a network instance on the given compute.
   */
  public InstanceRequestType requestType;

  /**
   * Link to secrets.
   */
  public String authCredentialsLink;

  /**
   * The resource pool the network exists in.
   */
  public String resourcePoolLink;

  /**
   * The network instance being realized.
   */
  public URI networkReference;

  /**
   * Task tracking the state of this request. Set by run-time.
   */
  public URI provisioningTaskReference;

  /**
   * Value indicating whether the service should treat this as a mock request and complete the
   * work flow without involving the underlying compute host infrastructure.
   */
  public boolean isMockRequest;
}
