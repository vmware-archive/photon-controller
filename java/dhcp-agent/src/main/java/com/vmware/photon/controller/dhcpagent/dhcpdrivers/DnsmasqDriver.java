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

import com.vmware.photon.controller.common.IpHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class implements Driver interface for Dnsmasq DHCP server.
 */
public class DnsmasqDriver implements DHCPDriver {
  private String dhcpLeaseFilePath = Constants.DNSMASQ_LEASE_PATH;
  private String dhcpHostFileDir = Constants.DNSMASQ_HOST_DIR_PATH;
  private String dhcpHostFileCopyDir = Constants.DNSMASQ_HOST_DIR_PATH + "-copy";
  private String dhcpOptionFileDir = Constants.DNSMASQ_OPTION_DIR_PATH;
  private String dhcpOptionFileCopyDir = Constants.DNSMASQ_OPTION_DIR_PATH + "-copy";
  private String dhcpConfigFilePath = Constants.DNSMASQ_CONF_PATH;

  public DnsmasqDriver(
      String dhcpLeaseFilePath,
      String dhcpHostFileDir,
      String dhcpOptionFileDir,
      String dhcpConfigFilePath) {
    this.dhcpLeaseFilePath = dhcpLeaseFilePath;
    this.dhcpHostFileDir = dhcpHostFileDir;
    this.dhcpHostFileCopyDir = dhcpHostFileDir + "-copy";
    this.dhcpOptionFileDir = dhcpOptionFileDir;
    this.dhcpOptionFileCopyDir = dhcpOptionFileDir + "-copy";
    this.dhcpConfigFilePath = dhcpConfigFilePath;

    for (String directory : new String[]{
        this.dhcpHostFileDir, this.dhcpHostFileCopyDir,
        this.dhcpOptionFileDir, this.dhcpOptionFileCopyDir}) {
      File dirFile = new File(directory);
      if (!dirFile.exists()) {
        dirFile.mkdir();
      }
    }
  }

  /**
   * This method attempt to reload the DHCP server's cache.
   * Return true if it was reloaded.
   *
   * @return
   */
  @Override
  public boolean reload() {
    boolean response = false;
    try {
      String command = "systemctl restart dnsmasq.service";
      Process p = Runtime.getRuntime().exec(command);
      boolean result = p.waitFor(Constants.TIMEOUT, TimeUnit.SECONDS);

      if (result && p.exitValue() == 0) {
        response = true;
      }
    } catch (Exception e) {
      // Swallow the exception--we'll return false, as appropriate
    }
    return response;
  }

  /**
   * This method returns true with DHCP server
   * is up and running.
   *
   * @return
   */

  @Override
  public boolean isRunning() {
    boolean response = false;
    try {
      String command = "systemctl is-active dnsmasq.service";
      Process p = Runtime.getRuntime().exec(command);
      boolean result = p.waitFor(Constants.TIMEOUT, TimeUnit.SECONDS);

      if (result && p.exitValue() == 0) {
        response = true;
      }
    } catch (Exception e) {
      // Swallow the exception--we'll return false, as appropriate
    }
    return response;
  }

  /**
   * This method creates subnet related configurations.
   *
   * @param subnetId
   * @param gateway
   * @param cidr
   * @param lowIp
   * @param highIp
   * @return
   * @throws Exception
   */
  @Override
  public Response createSubnet(
      String subnetId,
      String gateway,
      String cidr,
      String lowIp,
      String highIp) throws Exception {

    String netmask = IpHelper.calculateNetmaskStringFromCidr(cidr);

    // Create option file for the new subnet.
    String newOptionFilePath = dhcpOptionFileCopyDir + "/" + subnetId;

    File newOptionFile = new File(newOptionFilePath);
    if (!newOptionFile.exists()) {
      newOptionFile.createNewFile();
    }

    PrintWriter newOptionFileWriter = new PrintWriter(newOptionFile, "UTF-8");
    newOptionFileWriter.println("tag:" + subnetId + ",3," + gateway);
    newOptionFileWriter.println("tag:" + subnetId + ",1," + netmask);
    newOptionFileWriter.close();

    String oldOptionFilePath = dhcpOptionFileDir + "/" + subnetId;
    File oldOptionFile = new File(oldOptionFilePath);

    Files.move(newOptionFile.toPath(), oldOptionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    // Add range to dnsmasq configuration file.
    PrintWriter configFileWriter = new PrintWriter(new FileWriter(dhcpConfigFilePath, true));
    configFileWriter.println("dhcp-range=tag:" + subnetId + "," + lowIp + "," + highIp);
    configFileWriter.close();

    Response response = new Response();
    response.exitCode = 0;
    return response;
  }

  /**
   * This method deletes subnet related configurations.
   *
   * @param subnetId
   * @return
   * @throws Exception
   */
  @Override
  public Response deleteSubnet(
      String subnetId) throws Exception {

    // Delete option file for the subnet.
    String optionFilePath = dhcpOptionFileDir + "/" + subnetId;
    File optionFile = new File(optionFilePath);
    Files.deleteIfExists(optionFile.toPath());

    // Remove range from dnsmasq configuration file.
    removeLinesFromFile(
        dhcpConfigFilePath,
        Arrays.asList(subnetId));

    // Remove host file if exist.
    String hostFilePath = dhcpHostFileDir + "/" + subnetId;
    File hostFile = new File(hostFilePath);
    if (hostFile.exists()) {
      hostFile.delete();
    }

    Response response = new Response();
    response.exitCode = 0;
    return response;
  }

  /**
   * This method updates subnet leases of
   * IP for MAC address.
   *
   * @param subnetId
   * @param ipAddressToMACAddressMap
   * @param version
   * @return
   */
  @Override
  public Response updateSubnet(
      String subnetId,
      Map<String, String> ipAddressToMACAddressMap,
      Long version) throws Exception {
    Response response = new Response();

    // Read the old subnet file, compare the version, and get a list of IP-MAC mapping that needs
    // to be removed.
    String oldSubnetFilename = dhcpHostFileDir + "/" + subnetId;
    File oldSubnetHostFile = new File(oldSubnetFilename);
    Map<String, String> ipToMacToRemove = new HashMap<>();

    if (oldSubnetHostFile.exists()) {
      BufferedReader reader = new BufferedReader(new FileReader(oldSubnetHostFile));

      try {
        String line = null;
        while ((line = reader.readLine()) != null) {
          Pattern versionPattern = Pattern.compile("^# Version=(?<version>[0-9]+)$");
          Matcher versionMatcher = versionPattern.matcher(line);
          if (versionMatcher.matches()) {
            Long oldVersion = Long.parseLong(versionMatcher.group("version"));

            if (oldVersion > version) {
              response.exitCode = 1;
              return response;
            }

            if (oldVersion.equals(version)) {
              response.exitCode = 0;
              return response;
            }
          } else {
            Pattern ipToMacPattern = Pattern.compile(
                "^(?<ip>([0-9]{1,3}.){3}[0-9]{1,3}),(?<mac>[0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2}),net:[0-9A-Za-z]+$");
            Matcher ipToMacMatcher = ipToMacPattern.matcher(line);
            if (ipToMacMatcher.matches()) {
              String ip = ipToMacMatcher.group("ip");
              String mac = ipToMacMatcher.group("mac");

              if (!ipAddressToMACAddressMap.containsKey(ip) ||
                  !ipAddressToMACAddressMap.get(ip).equals(mac)) {
                ipToMacToRemove.put(ip, mac);
              }
            }
          }
        }
      } finally {
        reader.close();
      }
    }

    // Update the subnet file with new version and IP-MAC mapping.
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

    // Remove obsolete IP-MAC lease.
    if (!ipToMacToRemove.isEmpty()) {
      removeLinesFromFile(dhcpLeaseFilePath,
          ipToMacToRemove.entrySet().stream()
              .map(entry -> entry.getValue() + " " + entry.getKey()).collect(Collectors.toList()));
    }

    response.exitCode = 0;
    return response;
  }

  private void removeLinesFromFile(String filePath,
                                  List<String> excludeLineContents) throws IOException {
    File file = new File(filePath);
    File tmpFile = new File(filePath + ".tmp");
    BufferedReader fileReader = new BufferedReader(new FileReader(file));
    PrintWriter tmpFileWriter = new PrintWriter(new FileWriter(tmpFile));
    String line = null;

    while ((line = fileReader.readLine()) != null) {
      boolean excludeLine = false;
      for (String excludeLineContent : excludeLineContents) {
        if (line.contains(excludeLineContent)) {
          excludeLine = true;
          break;
        }
      }

      if (excludeLine) {
        continue;
      }

      tmpFileWriter.println(line);
    }

    fileReader.close();
    tmpFileWriter.close();

    Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    if (tmpFile.exists()) {
      tmpFile.delete();
    }
  }
}
