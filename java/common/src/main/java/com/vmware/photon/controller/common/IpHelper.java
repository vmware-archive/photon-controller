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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility methods for dealing with IP addresses.
 */
public class IpHelper {

  /**
   * Convert an Inet4Address to host byte order long.
   *
   * @param ip IPv4 address
   * @return host byte order equivalent
   */
  public static long ipToLong(Inet4Address ip) {
    byte[] octets = ip.getAddress();
    long result = 0;

    for (byte octet : octets) {
      result <<= 8;
      result |= octet & 0xff;
    }
    return result;
  }

  /**
   * Convert an host byte order long to InetAddress.
   *
   * @param ip long value storing ipv4 bytes in host byte order
   * @return InetAddress
   */
  public static InetAddress longToIp(long ip) {
    try {
      return InetAddress.getByAddress(longToNetworkByteOrderArray(ip));
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid IP " + ip);
    }
  }

  /**
   * Convert an host byte order long to byte array in network byte order.
   *
   * @param addr long value storing ipv4 bytes in host byte order
   * @return byte array in network byte order
   */
  public static byte[] longToNetworkByteOrderArray(long addr) {
    return new byte[]{(byte) (addr >>> 24), (byte) (addr >>> 16), (byte) (addr >>> 8),
        (byte) addr};
  }
}
