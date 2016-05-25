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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.Executors;

/**
 * Tests {@link StatusService}.
 */
public class StatusServiceTest {

  private ListeningExecutorService listeningExecutorService;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link StatusService#handleGet(com.vmware.xenon.common.Operation)}.
   */
  public class HandleGetTest {

    private TestEnvironment testEnvironment;

    private ListeningExecutorService listeningExecutorService;

    @BeforeMethod
    public void setUp() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      testEnvironment = TestEnvironment.create(
              new DnsmasqDriver(ReleaseIPServiceTest.class.getResource("/dnsmasq.leases").getPath(),
                      "/usr/local/bin/dhcp_release",
                      ReleaseIPServiceTest.class.getResource("/scripts/release-ip.sh").getPath(),
                      ReleaseIPServiceTest.class.getResource("/scripts/dhcp-status.sh").getPath()),
              1,
              listeningExecutorService);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      testEnvironment.stop();
      testEnvironment = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test
    public void testReady() throws Throwable {
      Status status = testEnvironment.getServiceState(StatusService.SELF_LINK, Status.class);
      assertThat(status.getType(), is(StatusType.READY));
    }
  }
}
