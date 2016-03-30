/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.nsxclient.datatypes;

/**
 * Class to hold the datatypes used for an NSX logical switch.
 */
public class NsxSwitch {
  /**
   * Replication modes for NSX logical switch.
   */
  public static enum ReplicationMode {
    /**
     * Stands for Mutilple Tunnel EndPoint
     * Each VNI will nominate one host Tunnel Endpoint as MTEP, which replicates
     * BUM traffic to other hosts within the same VNI.
     */
    MTEP,
    /**
     * Hosts create a copy of each BUM frame and send copy to each tunnel
     * endpoint that it knows for each VNI.
     */
    SOURCE
  }

  /**
   * Admin state for NSX logical switch.
   */
  public static enum AdminState {
    /**
     * Being managed by nsx manager.
     */
    UP,
    /**
     * Not being managed by nsx manager.
     */
    DOWN
  }
}
