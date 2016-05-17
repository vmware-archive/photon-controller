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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Class implements Driver interface for Dnsmasq DHCP server.
 */
public class DnsmasqDriver implements DHCPDriver {
    private String releaseIPUtilityPath = "/usr/local/bin/dhcp_release";
    private String releaseIPPath = "/scripts/release-ip";
    private String dhcpStatusPath = "/script/dhcp-status";

    public DnsmasqDriver(String utilityPath,
                         String releaseIPPath,
                         String dhcpStatusPath) {

        this.releaseIPUtilityPath = utilityPath;
        this.releaseIPPath = releaseIPPath;
        this.dhcpStatusPath = dhcpStatusPath;
    }

    public Response releaseIP(String networkInterface, String ipAddress, String macAddress) {
        Response response = new Response();

        try {
            String command = String.format("%s %s %s %s", releaseIPUtilityPath, networkInterface,
                    ipAddress, macAddress);
            Process p = Runtime.getRuntime().exec(command);
            int exitVal = p.waitFor();

           if (exitVal != 0) {
               response.exitCode = exitVal;

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

    public boolean isRunning() {
        boolean response = false;
        try {
            String command = dhcpStatusPath + " dnsmasq.service";
            Process p = Runtime.getRuntime().exec(command);
            int exitVal = p.waitFor();

            if (exitVal == 0) {
                response = true;
            }
        } catch (IOException e) {
            return response;
        } finally {
            return response;
        }
    }
}
