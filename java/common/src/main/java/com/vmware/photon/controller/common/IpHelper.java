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

import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;

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
   * Convert an ip in long format to an ip string.
   *
   * @param ip
   * @return
   */
  public static String longToIpString(long ip) {
    InetAddress ipAddress = longToIp(ip);
    return ipAddress.getHostAddress();
  }

  /**
   * Convert an ip string to long.
   *
   * @param ip
   * @return
   */
  public static long ipStringToLong(String ip) {
    InetAddress ipAddress = InetAddresses.forString(ip);

    if (!(ipAddress instanceof Inet4Address)) {
      throw new IllegalArgumentException(ip + " is not an IPv4 address");
    }
    return IpHelper.ipToLong((Inet4Address) ipAddress);
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

  /**
   * Calculate the CIDR from a given ip range.
   * Valid ranges are the ones that can be covered with a single CIDR and that
   * is validated by making sure that the inverseSubnet+1 is a power of 2.
   *
   * @param ipLow
   * @param ipHigh
   * @return
   */
  public static String calculateCidrFromIpV4Range(long ipLow, long ipHigh) {

    Preconditions.checkArgument(ipLow <= ipHigh,
        String.format("ipLow should be less than or equal to ipHigh, ipLow=%s, ipHigh=%s", ipLow, ipHigh));

    int inverseSubnetMask = (int) (ipHigh - ipLow);
    int subnetMask = ~inverseSubnetMask;

    Preconditions.checkState((inverseSubnetMask == 0) || ((inverseSubnetMask & (inverseSubnetMask + 1)) == 0),
        String.format(
            "inverseSubnetMask should be 0 or " +
                "inverseSubnetMask + 1 should be a power of 2, " +
                "ipLow=%s, ipHigh=%s, inverseSubnetMask=%s[%s], subnetMask=%s",
            Long.toBinaryString(ipLow),
            Long.toBinaryString(ipHigh),
            inverseSubnetMask,
            Long.toBinaryString(inverseSubnetMask),
            Long.toBinaryString(subnetMask)));

    Preconditions.checkState((ipLow & subnetMask) == ipLow,
        String.format("ipLow & subnetMask should equal ipLow, ipLow = %s, ipHigh= %s, subnetMask= %s",
            Long.toBinaryString(ipLow),
            Long.toBinaryString(ipHigh),
            Long.toBinaryString(subnetMask)));

    int cidr = (32 - Integer.numberOfTrailingZeros(subnetMask));
    InetAddress subnetAddress = longToIp(ipLow);
    return subnetAddress.getHostAddress() + "/" + cidr;
  }

  /**
   * Converts a long to int only if the long value is small enough to fit into an int.
   *
   * @param l
   * @return
   */
  public static int safeLongToInt(long l) {
    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(l
          + " cannot be cast to int without changing its value.");
    }
    return (int) l;
  }
}
