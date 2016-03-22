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

import org.testng.Assert;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

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
      assertThat(in.toString(), is(equalTo(out.toString())));
    } catch (Throwable e) {
      Assert.fail();
    }
  }

  // test getting an unused IP
  @Test
  public void testAddRemove() {
    try {
      IPRange range = new IPRange(InetAddress.getByName("127.0.0.1"),
          InetAddress.getByName("127.0.0.100"));
      range.setUsed(InetAddress.getByName("127.0.0.1"));
      assertThat("/127.0.0.2", is(equalTo(range.getNextUnused().toString())));
    } catch (Throwable e) {
      Assert.fail();
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
      assertThat("/127.0.0.2", is(equalTo(range.getNextUnused().toString())));
    } catch (Throwable e) {
      Assert.fail();
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
      assertThat(range.getNextUnused(), is(nullValue()));
      Assert.fail();
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
        assertThat(ip, is(notNullValue()));

        assertThat(ipMap.containsKey(ip.toString()), is(false));
        ipMap.put(ip.toString(), ip);
      }
    } catch (Throwable e) {
      Assert.fail();
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
      assertThat(range.isInRange(InetAddress.getByName("127.0.0.3")), is(false));
    } catch (Throwable e) {
      Assert.fail();
    }
  }
}
