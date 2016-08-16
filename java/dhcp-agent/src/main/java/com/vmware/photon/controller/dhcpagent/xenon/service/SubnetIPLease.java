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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import java.util.Map;

/**
 * This class holds the structure for subnet IP and MAC address
 * lease information.
 */
public class SubnetIPLease {

    /**
     * The unique id of the subnet.
     */
    public String subnetId;

    /**
     * The map containing IP to MAC address association.
     */
    public Map<String, String> ipToMACAddressMap;

    /**
     * The type of operation update or delete for subnet information.
     */
    public SubnetOperation subnetOperation;

    /**
     * Step state.
     */
    public enum SubnetOperation {
        UPDATE,
        DELETE,
    }
}
