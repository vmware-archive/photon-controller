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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class implements tests for Dnsmasq driver.
 */
public class DnsmasqDriverTest {

  private static final String successScript = "/scripts/success.sh";
  private static final String failureScript = "/scripts/failure.sh";
  private DnsmasqDriver dnsmasqDriver;

  @BeforeClass
  public void setUpClass() {
    try {
      String command = String.format("chmod +x %s",
          DnsmasqDriverTest.class.getResource(successScript).getPath());
      Runtime.getRuntime().exec(command);
      command = String.format("chmod +x %s",
          DnsmasqDriverTest.class.getResource(failureScript).getPath());
      Runtime.getRuntime().exec(command);
    } catch (IOException e) {
      fail(String.format("Failed with IOException: %s", e.toString()));
    }
  }

  public void setUpDriver(String scriptPath, String leaseFilePath) {
    dnsmasqDriver = new DnsmasqDriver(
        leaseFilePath,
        DnsmasqDriverTest.class.getResource("/hosts").getPath(),
        DnsmasqDriverTest.class.getResource("/options").getPath(),
        DnsmasqDriverTest.class.getResource("/config/dnsmasq.conf").getPath());
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  public void dummy() {
  }

  @Test
  public void testCreateSubnet() {
    try {
      setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());
      String gateway = "192.168.1.1";
      String cidr = "192.168.1.0/8";

      dnsmasqDriver.createSubnet("subnet1", gateway, cidr, "192.168.1.0", "192.168.1.16");

      FileReader optionFileReader = new FileReader(
          DnsmasqDriverTest.class.getResource("/options").getPath() + "/subnet1");
      BufferedReader optionFileBufferedReader = new BufferedReader(optionFileReader);

      String optionFileLine;
      boolean hasGatewayLine = false;
      boolean hasNetmaskLine = false;
      while ((optionFileLine = optionFileBufferedReader.readLine()) != null) {
        if (optionFileLine.contains("tag:subnet1,3,192.168.1.1")) {
          hasGatewayLine = true;
        } else if (optionFileLine.contains("tag:subnet1,1,255.0.0.0")) {
          hasNetmaskLine = true;
        }
      }

      if (!hasGatewayLine) {
        fail("Missing gateway line in option file");
      }

      if (!hasNetmaskLine) {
        fail("Missing netmask line in option file");
      }
      optionFileBufferedReader.close();

      FileReader configFileReader = new FileReader(
          DnsmasqDriverTest.class.getResource("/config/dnsmasq.conf").getPath());
      BufferedReader configFileBufferedReader = new BufferedReader(configFileReader);

      String configFileLine;
      boolean hasConfigFileLine = false;
      while ((configFileLine = configFileBufferedReader.readLine()) != null) {
        if (!configFileLine.contains("subnet1")) {
          continue;
        }

        if (!configFileLine.equals("dhcp-range=tag:subnet1,192.168.1.0,192.168.1.16")) {
          fail("Wrong content of the config file: " + configFileLine);
        }

        hasConfigFileLine = true;
        break;
      }

      if (!hasConfigFileLine) {
        fail("Config file does not contain dhcp range for the subnet");
      }
      configFileBufferedReader.close();
    } catch (Throwable e) {
      fail("Failed with exception: " + e.getMessage());
    }
  }

  @Test
  public void testDeleteSubnet() {
    try {
      setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());
      dnsmasqDriver.deleteSubnet("subnet1");

      File file = new File(DnsmasqDriverTest.class.getResource("/options").getPath() + "/subnet1");
      if (file.exists() && !file.isDirectory()) {
        fail("Option file not deleted");
      }

      FileReader configFileReader = new FileReader(
          DnsmasqDriverTest.class.getResource("/config/dnsmasq.conf").getPath());
      BufferedReader configFileBufferedReader = new BufferedReader(configFileReader);

      String configFileLine;
      boolean hasConfigFileLine = false;
      while ((configFileLine = configFileBufferedReader.readLine()) != null) {
        if (!configFileLine.contains("subnet1")) {
          continue;
        }

        hasConfigFileLine = true;
        break;
      }

      if (hasConfigFileLine) {
        fail("Dhcp range for the subnet was not removed from config file");
      }

      // We need to create a subnet configuration file so that other tests can have this file when setting
      // up the dnsmasq driver.
      dnsmasqDriver.createSubnet("subnet1", "192.168.1.1", "192.168.1.0/8", "192.168.1.0", "192.168.1.16");
      configFileBufferedReader.close();
    } catch (Throwable e) {
      fail("Failed with exception: " + e.getMessage());
    }
  }

  @Test
  public void testUpdateSubnetIPAllocation() {
    try {
      setUpDriver(successScript, DnsmasqDriverTest.class.getResource("/dnsmasq.leases").getPath());
      String ipAddress = "192.168.0.2";
      String macAddress = "08:00:27:d8:7d:8e";
      Map<String, String> macAddressIPMap = new HashMap<>();
      macAddressIPMap.put(ipAddress, macAddress);

      dnsmasqDriver.updateSubnet("subnet1", macAddressIPMap, 1L);

      FileReader subnetHostFile = new FileReader(
          DnsmasqDriverTest.class.getResource("/hosts").getPath() + "/subnet1");
      BufferedReader bufferedReader =
          new BufferedReader(subnetHostFile);

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        if (line.contains("Version")) {
          assertEquals(line, "# Version=1");
          continue;
        }

        if (!line.contains(ipAddress)) {
          fail(String.format("IP address not found in host file:", ipAddress));
        }

        if (!line.contains(macAddress)) {
          fail(String.format("MAC address not found in host file:", macAddress));
        }
      }
      bufferedReader.close();

    } catch (Throwable e) {
      fail(String.format("Failed with exception: %s", e.toString()));
    }
  }
}
