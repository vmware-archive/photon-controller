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

package com.vmware.photon.controller.apife.exceptions.external;

import com.vmware.photon.controller.api.model.ClusterType;

/**
 * Exception thrown when a certain type cluster is not configured.
 */
public class ClusterTypeNotConfiguredException extends ExternalException {

  private ClusterType clusterType;

  public ClusterTypeNotConfiguredException(ClusterType clusterType) {
    super(ErrorCode.CLUSTER_TYPE_NOT_CONFIGURED);
    this.clusterType = clusterType;
  }

  @Override
  public String getMessage() {
    if (clusterType != null) {
      return this.clusterType.toString() + " cluster is not configured yet.";
    } else {
      return "A valid and non-null cluster type must be provided";
    }
  }
}
