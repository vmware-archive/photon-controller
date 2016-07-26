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
    assertThat(out.toString(), is(equalTo("/" + expectedIpAddress)));
  }

  @Test(dataProvider = "IpAddresses")
  public void testLongToDottedIp(String expectedIpAddress, long value) {
    assertThat(IpHelper.longToDottedIp(value), is(expectedIpAddress));
  }

  @DataProvider(name = "IpAddresses")
  public Object[][] getAutoInitializedFieldsParams() {
    return new Object[][]{
        {"127.0.0.1", 0x7F000001L},
        {"192.168.0.1", 0xC0A80001L},
        {"0.0.0.0", 0x0L},
        {"255.255.255.255", 0xFFFFFFFFL},
    };
  }
}
