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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class implements Driver interface for Dnsmasq DHCP server.
 */
public class DnsmasqDriver implements DHCPDriver {
    private String dhcpLeaseFilePath = "/var/lib/misc/dnsmasq.leases";
    private String dhcpReleaseUtilityPath = Constants.DHCP_RELEASE_PATH;
    private String releaseIPPath = "/script/release-ip.sh";
    private String dhcpHostFileDir = Constants.DNSMASQ_HOST_DIR_PATH;
    private String dhcpHostFileCopyDir = Constants.DNSMASQ_HOST_DIR_PATH + "-copy";
    private String dhcpOptionFileDir = Constants.DNSMASQ_OPTION_DIR_PATH;
    private String dhcpOptionFileCopyDir = Constants.DNSMASQ_OPTION_DIR_PATH + "-copy";
    private String dhcpPidFilePath = Constants.DNSMASQ_PID_PATH;
    private String dhcpReloadCachePath = "/script/dhcp-reload.sh";

    public DnsmasqDriver(String dhcpLeaseFilePath,
            String dhcpReleaseUtilityPath, String releaseIPPath,
            String dhcpHostFileDir, String dhcpOptionFileDir, String dhcpPidFilePath,
            String dhcpReloadCachePath) {
        this.dhcpLeaseFilePath = dhcpLeaseFilePath;
        this.dhcpReleaseUtilityPath = dhcpReleaseUtilityPath;
        this.releaseIPPath = releaseIPPath;
        this.dhcpHostFileDir = dhcpHostFileDir;
        this.dhcpHostFileCopyDir = dhcpHostFileDir + "-copy";
        this.dhcpOptionFileDir = dhcpOptionFileDir;
        this.dhcpOptionFileCopyDir = dhcpOptionFileDir + "-copy";
        this.dhcpPidFilePath = dhcpPidFilePath;
        this.dhcpReloadCachePath = dhcpReloadCachePath;

        for (String directory : new String[] {
            this.dhcpHostFileDir, this.dhcpHostFileCopyDir,
            this.dhcpOptionFileDir, this.dhcpOptionFileCopyDir}) {
            File dirFile = new File(directory);
            if (!dirFile.exists()) {
                dirFile.mkdir();
            }
        }
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
            boolean result = p.waitFor(Constants.TIMEOUT, TimeUnit.SECONDS);

            int exitValue = p.exitValue();
           if (!result || exitValue != 0) {
               response.exitCode = exitValue;

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
     * This method attempt to reload the DHCP server's cache.
     * Return true if it was reloaded.
     *
     * @return
     */
    public boolean reload() {
        boolean response = false;
        try {
            String command = dhcpReloadCachePath + " " + dhcpPidFilePath;
            Process p = Runtime.getRuntime().exec(command);
            boolean result = p.waitFor(Constants.TIMEOUT, TimeUnit.SECONDS);

            if (result && p.exitValue() == 0) {
                response = true;
            }
        } catch (IOException e) {
            return response;
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
            String command = "systemctl is-active dnsmasq.service";
            Process p = Runtime.getRuntime().exec(command);
            boolean result = p.waitFor(Constants.TIMEOUT, TimeUnit.SECONDS);

            if (result && p.exitValue() == 0) {
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

    /**
     * This method creates subnet configuration.
     *
     * @param subnetId
     * @param gateway
     * @param cidr
     * @return
     * @throws Exception
     */
    public Response createSubnetConfiguration(
        String subnetId,
        String gateway,
        String cidr) throws Exception {

        String newFileName = dhcpOptionFileCopyDir + "/" + subnetId;

        File newFile = new File(newFileName);
        if (!newFile.exists()) {
            newFile.createNewFile();
        }

        PrintWriter writer = new PrintWriter(newFileName, "UTF-8");
        writer.println("tag:" + subnetId + ",3," + gateway + ",1," + cidr);
        writer.close();

        String oldFilename = dhcpOptionFileDir + "/" + subnetId;
        File oldFile = new File(oldFilename);

        Files.move(newFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Response response = new Response();
        response.exitCode = 0;
        return response;
    }

    /**
     * This method deletes subnet configuration.
     *
     * @param subnetId
     * @return
     * @throws Exception
     */
    public Response deleteSubnetConfiguration(
        String subnetId) throws Exception {

        Response response = new Response();
        String filename = dhcpOptionFileDir + "/" + subnetId;
        File file = new File(filename);
        Files.deleteIfExists(file.toPath());
        response.exitCode = 0;
        return response;
    }

    /**
     * This method update subnet leases of
     * IP for MAC address.
     *
     * @param subnetId
     * @param ipAddressToMACAddressMap
     * @param version
     *
     * @return
     */
    public Response updateSubnetIPLease(
        String subnetId,
        Map<String, String> ipAddressToMACAddressMap,
        Long version) throws Exception {
        Response response = new Response();

        String oldSubnetFilename = dhcpHostFileDir + "/" + subnetId;
        File oldSubnetHostFile = new File(oldSubnetFilename);
        if (oldSubnetHostFile.exists()) {
          FileInputStream inputStream = new FileInputStream(oldSubnetFilename);
          BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

          try {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
              Pattern pattern = Pattern.compile("^# Version=(?<version>[0-9]+)$");
              Matcher matcher = pattern.matcher(line);
              if (matcher.matches()) {
                Long oldVersion = Long.parseLong(matcher.group("version"));

                if (oldVersion > version) {
                  response.exitCode = 1;
                  return response;
                }

                if (oldVersion == version) {
                  response.exitCode = 0;
                  return response;
                }
              }
            }
          } finally {
            reader.close();
            inputStream.close();
          }
        }

        String newSubnetFilename = dhcpHostFileCopyDir + "/" + subnetId;

        PrintWriter writer = new PrintWriter(newSubnetFilename, "UTF-8");
        writer.println("# Version=" + version);
        for (Map.Entry<String, String> pair : ipAddressToMACAddressMap.entrySet()) {
            String line = pair.getKey() + "," + pair.getValue() + ",net:" + subnetId;
            writer.println(line);
        }

        writer.close();
        File newSubnetHostFile = new File(newSubnetFilename);

        Files.move(newSubnetHostFile.toPath(), oldSubnetHostFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        response.exitCode = 0;
        return response;
    }

    /**
     * This method deletes subnet IP leases.
     *
     * @param subnetId
     *
     * @return
     */
    public Response deleteSubnetIPLease(String subnetId) throws Exception {
        Response response = new Response();
        String subnetFilename = dhcpHostFileDir + "/" + subnetId;
        File subnetHostFile = new File(subnetFilename);
        Files.deleteIfExists(subnetHostFile.toPath());
        response.exitCode = 0;
        return response;
    }
}
