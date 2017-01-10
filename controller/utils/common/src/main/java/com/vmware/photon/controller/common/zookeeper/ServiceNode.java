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

package com.vmware.photon.controller.common.zookeeper;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * ServiceNode interface describes how services will join and leave
 * clusters. Potential implementations can include either no-constraint
 * cluster where anyone can join or a leader-elected cluster where
 * service only joins once it wins leader election.
 */
public interface ServiceNode {

  ListenableFuture<Lease> join();

  void leave();

  /**
   * Client lease: it gets expired when node disconnects from Zookeeper.
   */
  interface Lease {
    ListenableFuture<Void> getExpirationFuture();
  }

}
