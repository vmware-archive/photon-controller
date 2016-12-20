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

package com.vmware.photon.controller.clustermanager.statuschecks;

import com.google.common.util.concurrent.FutureCallback;

/**
 * Defines the interface for determining the status of Cluster Node(s).
 */
public interface StatusChecker {
  /**
   * Determines the status of a single or multiple nodes in a cluster.
   * Returns TRUE if the node(s) is ready. Otherwise returns false.
   *
   * @param nodeAddress      Address of the Node whose status is being checked.
   * @param callback         Callback method that will be invoked with a flag representing if the Node(s) are Ready.
   */
  void checkNodeStatus(final String nodeAddress,
                       final FutureCallback<Boolean> callback);
}
