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

package com.vmware.photon.controller.dhcpagent.dhcpdrivers;

import java.util.Map;

/**
 * Interface defining the functionality to be implemented by all DHCP drivers.
 */
public interface DHCPDriver {

    /**
     * This method calls DHCP driver to release IP
     * for cleanup of network resources.
     *
     * @param networkInterface
     * @param macAddress
     *
     * @return
     */
    Response releaseIP(String networkInterface, String macAddress);

    /**
     * This method returns true with DHCP server
     * is up and running.
     *
     * @return
     */
    boolean isRunning();

    /**
     * This method update subnet allocation of
     * IP for MAC address.
     *
     * @param subnetId
     * @param ipAddressToMACAddressMap
     *
     * @return
     */
    Response updateSubnetIPLease(String subnetId, Map<String, String> ipAddressToMACAddressMap) throws Exception;

    /**
     * This method deletes subnet.
     *
     * @param subnetId
     *
     * @return
     */
     Response deleteSubnetIPLease(String subnetId) throws Exception;

    /**
     * Class to hold the response for Driver operations.
     */
    class Response {
        public int exitCode = 0;
        public String stdError = "";
    }
}
