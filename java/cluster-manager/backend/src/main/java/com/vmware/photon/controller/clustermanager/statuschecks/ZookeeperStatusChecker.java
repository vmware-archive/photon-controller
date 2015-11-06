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

package com.vmware.photon.controller.clustermanager.statuschecks;

import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines the Status of a Zookeeper Node.
 */
public class ZookeeperStatusChecker implements StatusChecker {

  private static final Logger logger = LoggerFactory.getLogger(ZookeeperStatusChecker.class);

  @Override
  public void checkNodeStatus(final String nodeAddress,
                              final FutureCallback<Boolean> callback) {
    logger.info("Checking Zookeeper: {}", nodeAddress);
    String connectionString = createConnectionString(nodeAddress);

    try (CuratorFramework zkClient = CuratorFrameworkFactory
        .newClient(
            connectionString,
            1000 /* session timeout ms*/,
            1000 /* connection timeout ms*/,
            new RetryOneTime(1))) {
      zkClient.start();
      zkClient.getData().forPath("/");

      callback.onSuccess(true);
    } catch (Exception e) {
      logger.warn("Failed to read data from ZK: ", e);
      callback.onSuccess(false);
    }
  }

  private static String createConnectionString(String serverAddress) {
    return serverAddress + ":" + ClusterManagerConstants.Mesos.ZOOKEEPER_PORT;
  }
}
