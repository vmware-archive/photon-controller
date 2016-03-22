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

package com.vmware.photon.controller.provisioner.xenon.entity;

import com.vmware.photon.controller.provisioner.xenon.helpers.IPRange;

import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements tests for the {@link IPRange} class.
 */
public class TestIPRange {

  // test marshalling
  @Test
  public void testIptoInttoIP() {
    try {
      InetAddress in = InetAddress.getByName("127.0.0.1");
      long cur = IPRange.ipToLong(in);

      InetAddress out = IPRange.longToIp(cur);
      assertEquals(in.toString(), out.toString());
    } catch (Throwable e) {
      fail();
    }
  }

  // test getting an unused IP
  @Test
  public void testAddRemove() {
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.0.0.100"));
      range.setUsed(InetAddress.getByName("127.0.0.1"));
      assertEquals("/127.0.0.2", range.getNextUnused().toString());
    } catch (Throwable e) {
      fail();
    }
  }

  // test if a sparse range gets the next unset ip.
  @Test
  public void testSparseRemove() {
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.0.0.100"));
      range.setUsed(InetAddress.getByName("127.0.0.1"));
      range.setUsed(InetAddress.getByName("127.0.0.3"));
      assertEquals("/127.0.0.2", range.getNextUnused().toString());
    } catch (Throwable e) {
      fail();
    }
  }

  // test if a full range throws an exception.
  @Test
  public void testFullRemove() {
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.0.0.2"));
      range.setUsed(InetAddress.getByName("127.0.0.1"));
      range.setUsed(InetAddress.getByName("127.0.0.2"));
      assertNull(range.getNextUnused());
      fail();
    } catch (Throwable e) {
      // should throw an exception.
    }
  }

  // test for collisions.
  @Test
  public void testCollisions() {
    Map<String, InetAddress> ipMap = new HashMap<>();
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.1.0.1"));
      for (int i = 0; i < 1000; i++) {
        InetAddress ip = range.getNextUnused();
        assertNotNull(ip);

        assertFalse(ipMap.containsKey(ip.toString()));
        ipMap.put(ip.toString(), ip);
      }
    } catch (Throwable e) {
      fail();
    }
  }

  // test if an ip is out of the range
  @Test
  public void testOutOfRange() {
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.0.0.2"));
      range.setUsed(InetAddress.getByName("127.0.0.1"));
      range.setUsed(InetAddress.getByName("127.0.0.2"));
      assertFalse(range.isInRange(InetAddress.getByName("127.0.0.3")));
    } catch (Throwable e) {
      fail();
    }
  }
}
