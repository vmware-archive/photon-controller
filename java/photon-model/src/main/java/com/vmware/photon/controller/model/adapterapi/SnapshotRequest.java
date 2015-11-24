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
 * Request to the snapshot service.
 */
public class SnapshotRequest {

  /**
   * URI reference to the snapshot service instance.
   */
  public URI snapshotReference;

  /**
   * URI reference to the snapshot task service instance.
   */
  public URI snapshotTaskReference;

  /**
   * Value indicating whether the service should treat this as a mock request and complete the
   * work flow without involving the underlying compute host infrastructure.
   */
  public boolean isMockRequest;
}
