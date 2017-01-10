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
     * This method returns true with DHCP server
     * is up and running.
     *
     * @return
     */
    boolean isRunning();

    /**
     * This method creates subnet configuration.
     *
     * @param subnetId
     * @param gateway
     * @param cidr
     * @param lowIp
     * @param highIp
     * @return
     * @throws Exception
     */
    Response createSubnet(
        String subnetId,
        String gateway,
        String cidr,
        String lowIp,
        String highIp) throws Exception;

    /**
     * This method deletes subnet configuration.
     *
     * @param subnetId
     * @return
     * @throws Exception
     */
    Response deleteSubnet(
        String subnetId) throws Exception;

    /**
     * This method updates subnet allocation of
     * IP for MAC address.
     *
     * @param subnetId
     * @param ipAddressToMACAddressMap
     * @param version
     *
     * @return
     */
    Response updateSubnet(
        String subnetId,
        Map<String, String> ipAddressToMACAddressMap,
        Long version) throws Exception;

    /**
     * This method attempt to reload the DHCP server's cache.
     * Return true if it was reloaded.
     *
     * @return
     */
     boolean reload();

    /**
     * Class to hold the response for Driver operations.
     */
    class Response {
        public int exitCode = 0;
        public String stdError = "";
    }
}
