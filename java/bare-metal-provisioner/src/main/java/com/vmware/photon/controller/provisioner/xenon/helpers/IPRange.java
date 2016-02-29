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

package com.vmware.photon.controller.provisioner.xenon.helpers;

import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetService;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.BitSet;

/**
 * Represents DhcpSubnet range.
 */
public class IPRange {
  public DhcpSubnetService.DhcpSubnetState.Range range = null;

  private long ipHi;
  private long ipLo;
  private BitSet bits;

  public IPRange(DhcpSubnetService.DhcpSubnetState.Range range)
      throws IllegalArgumentException {
    try {
      this.ipLo = ipToLong(InetAddress.getByName(range.low));
      this.ipHi = ipToLong(InetAddress.getByName(range.high));
      this.range = range;
      if (range.usedIps != null && range.usedIps.length != 0) {
        this.bits = BitSet.valueOf(range.usedIps);
      } else {
        this.bits = new BitSet(this.size());
      }
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("not an ip");
    }
  }

  public IPRange(InetAddress ipLo, InetAddress ipHi) {
    this.ipLo = ipToLong(ipLo);
    this.ipHi = ipToLong(ipHi);
    this.bits = new BitSet(this.size());
  }

  public int size() {
    return safeLongToInt(this.ipHi - this.ipLo) + 1;
  }

  /**
   * Set an ip as used in the range.
   *
   * @param ip
   */
  public void setUsed(InetAddress ip) {
    if (!this.isInRange(ip)) {
      throw new IllegalArgumentException("not in range");
    }

    int cur = safeLongToInt(ipToLong(ip) - this.ipLo);
    this.bits.set(cur);
  }

  /**
   * Set an ip as used in the range.
   *
   * @param ip
   */
  public void unSetUsed(InetAddress ip) {
    if (!this.isInRange(ip)) {
      throw new IllegalArgumentException("not in range");
    }

    int cur = safeLongToInt(ipToLong(ip) - this.ipLo);
    this.bits.clear(cur);
  }

  public InetAddress getNextUnused() {
    if (this.isFull()) {
      throw new ArrayIndexOutOfBoundsException("range is full");
    }
    int cur = this.bits.nextClearBit(0);
    this.bits.set(cur);
    return longToIp(cur + this.ipLo);
  }

  public boolean isFull() {
    return (this.bits.length() >= this.size());
  }

  /**
   * Test whether an IP is in the range.
   *
   * @param ip
   * @return
   */
  public boolean isInRange(InetAddress ip) {
    if (ip instanceof Inet6Address) {
      throw new IllegalArgumentException("not ipv4");
    }
    long cur = ipToLong(ip);
    return (this.ipLo <= cur && cur <= this.ipHi);
  }

  /**
   * Convert an InetAddress to host byte order 32-bit int.
   *
   * @param ip
   * @return host byte order equivalent
   */
  public static long ipToLong(InetAddress ip) {
    if (ip instanceof Inet6Address) {
      throw new IllegalArgumentException("not ipv4");
    }
    byte[] octets = ip.getAddress();
    long result = 0;

    for (byte octet : octets) {
      result <<= 8;
      result |= octet & 0xff;
    }
    return result;
  }

  public static InetAddress longToIp(long ip) {
    try {
      return InetAddress.getByAddress(toIPByteArray(ip));
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("not an ip");
    }
  }

  private static byte[] toIPByteArray(long addr) {
    return new byte[]{ (byte) (addr >>> 24), (byte) (addr >>> 16), (byte) (addr >>> 8),
        (byte) addr };
  }

  private static int safeLongToInt(long l) {
    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(l
          + " cannot be cast to int without changing its value.");
    }
    return (int) l;
  }

  public byte[] toByteArray() {
    return bits.toByteArray();
  }
}
