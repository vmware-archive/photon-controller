/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.model.resources;

import java.net.URI;

/**
 * Request to create/destroy a network instance on a given compute.
 */
public class NetworkInstanceRequest {

  /**
   * The types of instance request.
   */
  public static enum InstanceRequestType {
    CREATE,
    DELETE
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
   * The resource pool the network exists in
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
   * work flow without involving the underlying compute host infrastructure
   */
  public boolean isMockRequest;
}
