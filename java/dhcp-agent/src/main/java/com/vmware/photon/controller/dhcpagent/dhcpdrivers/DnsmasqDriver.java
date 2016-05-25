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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Class implements Driver interface for Dnsmasq DHCP server.
 */
public class DnsmasqDriver implements DHCPDriver {
    private String dhcpLeaseFilePath = "/var/lib/misc/dnsmasq.leases";
    private String dhcpReleaseUtilityPath = "/usr/local/bin/dhcp_release";
    private String releaseIPPath = "/script/release-ip.sh";
    private String dhcpStatusPath = "/script/dhcp-status.sh";

    public DnsmasqDriver(String dhcpLeaseFilePath,
            String dhcpReleaseUtilityPath, String releaseIPPath, String dhcpStatusPath) {
        this.dhcpLeaseFilePath = dhcpLeaseFilePath;
        this.dhcpReleaseUtilityPath = dhcpReleaseUtilityPath;
        this.releaseIPPath = releaseIPPath;
        this.dhcpStatusPath = dhcpStatusPath;
    }

    /**
     * This method calls DHCP driver to release IP
     * for cleanup of network resources.
     *
     * @param networkInterface
     * @param macAddress
     *
     * @return
     */
    public Response releaseIP(String networkInterface, String macAddress) {
        Response response = new Response();

        try {
            String ipAddress = findIP(macAddress);
            String command = String.format("%s %s %s %s %s", releaseIPPath, dhcpReleaseUtilityPath, networkInterface,
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
        } catch (Exception e) {
            response.exitCode = -1;
            response.stdError = e.toString();
        } finally {
            return response;
        }
    }

    /**
     * This method returns true with DHCP server
     * is up and running.
     *
     * @return
     */
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

    /**
     * This method parses Dnsmasq lease file to
     * get IP for the macaddress provided.
     *
     * @param macAddress
     *
     * @return
     */
    public String findIP(String macAddress) throws Throwable {
        String ipAddress = "";

        try (BufferedReader br = new BufferedReader(new FileReader(dhcpLeaseFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String [] leaseInfo = line.split("\\s+");
                if (leaseInfo[1].equalsIgnoreCase(macAddress)) {
                    return leaseInfo[2];
                }
            }
        }

        return ipAddress;
    }
}
