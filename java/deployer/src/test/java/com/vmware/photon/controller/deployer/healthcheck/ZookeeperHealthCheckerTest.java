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

import org.apache.curator.test.TestingServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Random;

/**
 * Implements tests for {@link ZookeeperHealthChecker}.
 */
public class ZookeeperHealthCheckerTest {


  private static final Logger logger = LoggerFactory.getLogger(ZookeeperHealthCheckerTest.class);

  private static final int UNUSABLE_PORT = 2;
  public static final int START_PORT = 10000;
  public static final int RETRIES = 10;
  public int port;

  @BeforeMethod
  public void beforeMethod() {
    port = START_PORT;
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
        "127.0.0.1", UNUSABLE_PORT);
    boolean response = zookeeperHealthChecker.isReady();

    assertFalse(response);
  }

  private TestingServer startZookeeper() {
    int retriesLeft = RETRIES;
    Random random = new Random();
    while (retriesLeft > 0) {
      try {
        return new TestingServer(port);
      } catch (Throwable ignore) {
        retriesLeft--;
        if (retriesLeft <= 0) {
          throw new RuntimeException("Zookeeper could not be started on port [" + port + "]");
        }
        logger.info("Could not start zookeeper on port [{}] will retry ({} retries left)", port, retriesLeft);
        port = START_PORT + random.nextInt(10000);
      }
    }
    return null;
  }
}
