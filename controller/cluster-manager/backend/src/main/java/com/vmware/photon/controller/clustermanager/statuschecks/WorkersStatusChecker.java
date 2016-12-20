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

import java.util.List;
import java.util.Set;

/**
 * Defines the interface for fetching set of worker nodes of a cluster.
 */
public interface WorkersStatusChecker {
  /**
   * Determines the status of a single or multiple nodes in a cluster.
   * Returns TRUE if the node(s) is ready. Otherwise returns false.
   *
   * @param masterAddress    Address of the master server that will be queried to check the status of the worker(s).
   * @param workerAddresses   Worker addresses that will be verified if they have been registered with the server
   * @param callback         Callback method that will be invoked with a flag representing if the Node(s) are Ready.
   */
  void checkWorkersStatus(final String masterAddress,
                          final List<String> workerAddresses,
                          final FutureCallback<Boolean> callback);

  /**
   * Fetches worker nodes of a cluster.
   * Returns set of worker nodes.
   *
   * @param serverAddress    Address of the Master that will be queried to check the status of the node(s).
   * @param callback         Callback method that will be invoked with set of worker nodes.
   */
  void getWorkersStatus(String serverAddress,
                        final FutureCallback<Set<String>> callback);
}
