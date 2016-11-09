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
import java.io.FileWriter;
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
  private String dhcpConfigFilePath = Constants.DNSMASQ_CONF_PATH;
  private String dhcpPidFilePath = Constants.DNSMASQ_PID_PATH;

  public DnsmasqDriver(
      String dhcpLeaseFilePath,
      String dhcpReleaseUtilityPath,
      String releaseIPPath,
      String dhcpHostFileDir,
      String dhcpOptionFileDir,
      String dhcpConfigFilePath,
      String dhcpPidFilePath) {
    this.dhcpLeaseFilePath = dhcpLeaseFilePath;
    this.dhcpReleaseUtilityPath = dhcpReleaseUtilityPath;
    this.releaseIPPath = releaseIPPath;
    this.dhcpHostFileDir = dhcpHostFileDir;
    this.dhcpHostFileCopyDir = dhcpHostFileDir + "-copy";
    this.dhcpOptionFileDir = dhcpOptionFileDir;
    this.dhcpOptionFileCopyDir = dhcpOptionFileDir + "-copy";
    this.dhcpConfigFilePath = dhcpConfigFilePath;
    this.dhcpPidFilePath = dhcpPidFilePath;

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
   * This method calls DHCP driver to release IP
   * for cleanup of network resources.
   *
   * @param networkInterface
   * @param macAddress
   * @return
   */
  @Override
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
    } catch (Throwable t) {
      response.exitCode = -1;
      response.stdError = t.toString();
    }
    return response;
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
    try (BufferedReader pid = new BufferedReader(new FileReader(dhcpPidFilePath))) {
      String command = "kill -SIGHUP \"$(< " + pid + " )\" ";
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
   * This method parses Dnsmasq lease file to
   * get IP for the macaddress provided.
   *
   * @param macAddress
   * @return
   */
  public String findIP(String macAddress) throws Throwable {
    String ipAddress = "";

    try (BufferedReader br = new BufferedReader(new FileReader(dhcpLeaseFilePath))) {
      String line;
      while ((line = br.readLine()) != null) {
        String[] leaseInfo = line.split("\\s+");
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
   * @param lowIp
   * @param highIp
   * @return
   * @throws Exception
   */
  @Override
  public Response createSubnetConfiguration(
      String subnetId,
      String gateway,
      String cidr,
      String lowIp,
      String highIp) throws Exception {

    // Create option file for the new subnet.
    String newOptionFilePath = dhcpOptionFileCopyDir + "/" + subnetId;

    File newOptionFile = new File(newOptionFilePath);
    if (!newOptionFile.exists()) {
      newOptionFile.createNewFile();
    }

    PrintWriter newOptionFileWriter = new PrintWriter(newOptionFile, "UTF-8");
    newOptionFileWriter.println("tag:" + subnetId + ",3," + gateway + ",1," + cidr);
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
   * This method deletes subnet configuration.
   *
   * @param subnetId
   * @return
   * @throws Exception
   */
  @Override
  public Response deleteSubnetConfiguration(
      String subnetId) throws Exception {

    // Delete option file for the subnet.
    String optionFilePath = dhcpOptionFileDir + "/" + subnetId;
    File optionFile = new File(optionFilePath);
    Files.deleteIfExists(optionFile.toPath());

    // Remove range from dnsmasq configuration file.
    File configFile = new File(dhcpConfigFilePath);
    File tmpConfigFile = new File(dhcpConfigFilePath + ".tmp");
    BufferedReader configFileReader = new BufferedReader(new FileReader(configFile));
    PrintWriter tmpConfigFileWriter = new PrintWriter(new FileWriter(tmpConfigFile));
    String line = null;

    while ((line = configFileReader.readLine()) != null) {
      if (!line.contains(subnetId)) {
        tmpConfigFileWriter.println(line);
      }
    }

    configFileReader.close();
    tmpConfigFileWriter.close();

    Files.move(tmpConfigFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    Response response = new Response();
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
   * @return
   */
  @Override
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
   * @return
   */
  @Override
  public Response deleteSubnetIPLease(String subnetId) throws Exception {
    Response response = new Response();
    String subnetFilename = dhcpHostFileDir + "/" + subnetId;
    File subnetHostFile = new File(subnetFilename);
    Files.deleteIfExists(subnetHostFile.toPath());
    response.exitCode = 0;
    return response;
  }
}
