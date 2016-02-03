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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.curator.test.TestingServer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Random;

/**
 * Implements tests for {@link ZookeeperHealthChecker}.
 */
public class ZookeeperHealthCheckerTest {

  public static final int startPort = 10000;
  public static final int retries = 10;
  public int port;

  @BeforeMethod
  public void beforeMethod() {
    port = startPort;
  }

  @Test
  public void testZookeeperRunning() throws Throwable {
    try (TestingServer zookeeper = startZookeeper()) {
      HealthChecker zookeeperHealthChecker = new ZookeeperHealthChecker(
          "127.0.0.1", port);
      boolean response = zookeeperHealthChecker.isReady();

      assertTrue(response);
    }
  }

  @Test
  public void testZookeeperDown() throws Throwable {
    HealthChecker zookeeperHealthChecker = new ZookeeperHealthChecker(
        "127.0.0.1", ServicePortConstants.ZOOKEEPER_PORT);
    boolean response = zookeeperHealthChecker.isReady();

    assertFalse(response);
  }

  private TestingServer startZookeeper() {
    int retriesLeft = retries;
    Random random = new Random();
    while (retriesLeft > 0) {
      try {
        return new TestingServer(port);
      } catch(Throwable ignore) {
        retriesLeft--;
        port = startPort + random.nextInt(10000);
        if (retries > 0) {
          throw new RuntimeException(ignore);
        }
      }
    }
    return null;
  }
}
