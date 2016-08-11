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
package com.vmware.photon.controller.common;

import com.google.common.net.InetAddresses;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Tests {@link IpHelper}.
 */
public class IpHelperTest {

  @Test(dataProvider = "IpAddresses")
  public void testIpToLong(String ipAddress, long expectedLongValue) {
    InetAddress in = InetAddresses.forString(ipAddress);
    assertThat(in, is(instanceOf(Inet4Address.class)));
    long ip = IpHelper.ipToLong((Inet4Address) in);
    assertThat(ip, is(expectedLongValue));
  }

  @Test(dataProvider = "IpAddresses")
  public void testLongToIp(String expectedIpAddress, long value) {
    InetAddress out = IpHelper.longToIp(value);
    assertThat(out.getHostAddress(), is(equalTo(expectedIpAddress)));
  }

  @Test(dataProvider = "IpAddresses")
  public void testLongToIpString(String expectedIpAddress, long value) {
    String out = IpHelper.longToIpString(value);
    assertThat(out, is(equalTo(expectedIpAddress)));
  }

  @Test(dataProvider = "IpAddresses")
  public void testIpStringToLong(String ipAddress, long expectedLongValue) {
    long ip = IpHelper.ipStringToLong(ipAddress);
    assertThat(ip, is(expectedLongValue));
  }

  @DataProvider(name = "IpAddresses")
  public Object[][] getIpAddresses() {
    return new Object[][]{
        {"127.0.0.1", 0x7F000001L},
        {"192.168.0.1", 0xC0A80001L},
        {"0.0.0.0", 0x0L},
        {"255.255.255.255", 0xFFFFFFFFL},
    };
  }

  @Test(dataProvider = "ValidIpRanges")
  public void testCidrCalculationSuccess(String startAddress, String endEddress, String expectedCidr) {
    InetAddress lowAddress = InetAddresses.forString(startAddress);
    InetAddress higAddress = InetAddresses.forString(endEddress);
    long lowIp = IpHelper.ipToLong((Inet4Address) lowAddress);
    long highIp = IpHelper.ipToLong((Inet4Address) higAddress);
    String cidr = IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
    assertThat(cidr, is(equalTo(expectedCidr)));
  }

  @Test(dataProvider = "InvalidIpRanges", expectedExceptions = Exception.class)
  public void testCidrCalculationFailure(String startAddress, String endEddress) {
    InetAddress lowAddress = InetAddresses.forString(startAddress);
    InetAddress higAddress = InetAddresses.forString(endEddress);
    long lowIp = IpHelper.ipToLong((Inet4Address) lowAddress);
    long highIp = IpHelper.ipToLong((Inet4Address) higAddress);
    String cidr = IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
  }

  @DataProvider(name = "ValidIpRanges")
  public Object[][] getValidIpRanges() {
    return new Object[][]{
        {"5.10.64.0", "5.10.127.255", "5.10.64.0/18"},
        {"5.10.64.0", "5.10.64.255", "5.10.64.0/24"},
        {"5.10.127.0", "5.10.127.255", "5.10.127.0/24"},
        {"127.0.0.1", "127.0.0.1", "127.0.0.1/32"},
        {"192.168.0.0", "192.168.0.127", "192.168.0.0/25"},
        {"192.168.0.128", "192.168.0.255", "192.168.0.128/25"},
        {"192.168.0.64", "192.168.0.127", "192.168.0.64/26"},
        {"192.168.0.0", "192.168.0.255", "192.168.0.0/24"},
        {"192.168.0.0", "192.168.255.255", "192.168.0.0/16"},
        {"10.0.0.0", "10.255.255.255", "10.0.0.0/8"},
        {"172.16.0.0", "172.31.255.255", "172.16.0.0/12"},
        {"0.0.0.0", "0.0.0.0", "0.0.0.0/32"},
        {"255.255.255.255", "255.255.255.255", "255.255.255.255/32"},
        {"0.0.0.0", "127.255.255.255", "0.0.0.0/1"},
        {"128.0.0.0", "255.255.255.255", "128.0.0.0/1"},
        {"128.0.0.0", "191.255.255.255", "128.0.0.0/2"},
        {"0.0.0.0", "255.255.255.255", "0.0.0.0/0"},
    };
  }

  @DataProvider(name = "InvalidIpRanges")
  public Object[][] getInvalidIpRanges() {
    return new Object[][]{
        {"5.10.64.0", "5.10.255.255"},
        {"5.10.127.0", "5.10.127.64"},
        {"0.0.0.1", "0.0.0.0"},
        {"127.0.0.1", "127.0.0.0"},
        {"255.255.255.255", "255.255.255.254"},
        {"192.168.0.0", "192.168.0.192"},
        {"192.168.0.64", "192.168.0.192"},
        {"192.168.0.64", "512.0.0.0"},
        {"512.0.0.0", "192.168.0.192"},
        {"512.0.0.0", "512.0.0.0"},
    };
  }
}
