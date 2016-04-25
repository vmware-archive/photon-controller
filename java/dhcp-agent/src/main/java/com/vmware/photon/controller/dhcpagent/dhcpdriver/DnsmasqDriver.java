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

package com.vmware.photon.controller.dhcpagent.dhcpdriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Class implements Driver interface for Dnsmasq DHCP server.
 */
public class DnsmasqDriver implements Driver {

    String utilityPath = "/usr/local/bin";
    String utility = "dhcp_release";

    public DnsmasqDriver(String utilityPath) {
        this.utilityPath = utilityPath;
    }

    public DriverResponse releaseIP(String networkInterface, String ipAddress, String macAddress) {
        DriverResponse response = new DriverResponse();

        try {
            String command = String.format("%s/%s %s %s %s", utilityPath, utility, networkInterface,
                    ipAddress, macAddress);
            Process p = Runtime.getRuntime().exec(command);

           if (p.exitValue() != 0) {
               response.exitCode = p.exitValue();

               String s;
               BufferedReader stdError = new BufferedReader(new
                       InputStreamReader(p.getErrorStream()));

               while ((s = stdError.readLine()) != null) {
                   response.stdError += s;
               }
            }
        } catch (IOException e) {
            response.exitCode = -1;
            response.stdError = e.toString();
        } finally {
            return response;
        }
    }
}
