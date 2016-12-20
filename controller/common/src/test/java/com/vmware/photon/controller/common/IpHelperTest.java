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
    long lowIp = IpHelper.ipStringToLong(startAddress);
    long highIp = IpHelper.ipStringToLong(endEddress);
    String cidr = IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
    assertThat(cidr, is(equalTo(expectedCidr)));
  }

  @Test(dataProvider = "InvalidIpRanges", expectedExceptions = Exception.class)
  public void testCidrCalculationFailure(String startAddress, String endEddress) {
    long lowIp = IpHelper.ipStringToLong(startAddress);
    long highIp = IpHelper.ipStringToLong(endEddress);
    IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
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

  @Test(dataProvider = "ValidCidr")
  public void testNetmaskStringCalculationSuccess(String cidr, String expectedNetmask) {
    String actualNetmask = IpHelper.calculateNetmaskStringFromCidr(cidr);
    assertThat(actualNetmask, equalTo(expectedNetmask));
  }

  @DataProvider(name = "ValidCidr")
  public Object[][] getValidCidr() {
    return new Object[][] {
        {"192.168.1.0/1", "128.0.0.0"},
        {"192.168.1.0/2", "192.0.0.0"},
        {"192.168.1.0/3", "224.0.0.0"},
        {"192.168.1.0/4", "240.0.0.0"},
        {"192.168.1.0/5", "248.0.0.0"},
        {"192.168.1.0/6", "252.0.0.0"},
        {"192.168.1.0/7", "254.0.0.0"},
        {"192.168.1.0/8", "255.0.0.0"},
        {"192.168.1.0/9", "255.128.0.0"},
        {"192.168.1.0/10", "255.192.0.0"},
        {"192.168.1.0/11", "255.224.0.0"},
        {"192.168.1.0/12", "255.240.0.0"},
        {"192.168.1.0/13", "255.248.0.0"},
        {"192.168.1.0/14", "255.252.0.0"},
        {"192.168.1.0/15", "255.254.0.0"},
        {"192.168.1.0/16", "255.255.0.0"},
        {"192.168.1.0/17", "255.255.128.0"},
        {"192.168.1.0/18", "255.255.192.0"},
        {"192.168.1.0/19", "255.255.224.0"},
        {"192.168.1.0/20", "255.255.240.0"},
        {"192.168.1.0/21", "255.255.248.0"},
        {"192.168.1.0/22", "255.255.252.0"},
        {"192.168.1.0/23", "255.255.254.0"},
        {"192.168.1.0/24", "255.255.255.0"},
        {"192.168.1.0/25", "255.255.255.128"},
        {"192.168.1.0/26", "255.255.255.192"},
        {"192.168.1.0/27", "255.255.255.224"},
        {"192.168.1.0/28", "255.255.255.240"},
        {"192.168.1.0/29", "255.255.255.248"},
        {"192.168.1.0/30", "255.255.255.252"},
        {"192.168.1.0/31", "255.255.255.254"},
        {"192.168.1.0/32", "255.255.255.255"},
    };
  }

  @Test(dataProvider = "InvalidCidr", expectedExceptions = Exception.class)
  public void testNetmaskStringCalculationFailure(String cidr) {
    IpHelper.calculateNetmaskStringFromCidr(cidr);
  }

  @DataProvider(name = "InvalidCidr")
  public Object[][] getInvalidCidr() {
    return new Object[][] {
        {""},
        {"192.168.1.5"},
        {"192.168.1.5/-1"},
        {"192.168.1.0/0"},
        {"192.168.1.5/33"},
        {"192.168.1.5/128"},
        {"192.168.1.5/abc"},
        {"192.168.1.5/2/3"}
    };
  }
}
