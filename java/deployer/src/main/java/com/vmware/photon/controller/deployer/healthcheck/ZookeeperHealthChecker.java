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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements health-check for Zookeeper.
 */
public class ZookeeperHealthChecker implements HealthChecker {

  private static final Logger logger = LoggerFactory.getLogger(ZookeeperHealthChecker.class);

  private final String ipAddress;
  private final int port;

  public ZookeeperHealthChecker(String ipAddress, int port) {
    this.ipAddress = ipAddress;
    this.port = port;
  }

  @Override
  public boolean isReady() {
    String zkConnectionString = String.format("%s:%s", this.ipAddress, this.port);

    logger.info("Checking Zookeeper: {}", zkConnectionString);

    try (CuratorFramework zkClient = CuratorFrameworkFactory
        .newClient(
            zkConnectionString,
            1000 /* session timeout ms*/,
            1000 /* connection timeout ms*/,
            new RetryOneTime(1))) {
      zkClient.start();
      zkClient.getData().forPath("/");

      return true;
    } catch (Exception e) {
      logger.warn("Failed to read data from ZK: ", e);
      return false;
    }
  }
}
