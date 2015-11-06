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

import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Determines the Status of a Swarm Node.
 */
public class SwarmStatusChecker implements StatusChecker, SlavesStatusChecker {

  private static final Logger logger = LoggerFactory.getLogger(SwarmStatusChecker.class);
  private SwarmClient swarmClient;

  public SwarmStatusChecker(SwarmClient swarmClient) {
    this.swarmClient = swarmClient;
  }

  @Override
  public void checkNodeStatus(final String serverAddress,
                              final FutureCallback<Boolean> callback) {
    Preconditions.checkNotNull(serverAddress, "serverAddress cannot be null");
    logger.info("Checking Swarm: {}", serverAddress);

    try {
      String connectionString = createConnectionString(serverAddress);
      swarmClient.getNodeAddressesAsync(connectionString, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> nodes) {
          if (!nodes.contains(serverAddress)) {
            logger.info("Node not registered with Swarm: {}", serverAddress);
            callback.onSuccess(false);
            return;
          }
          callback.onSuccess(true);
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Swarm call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Swarm call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void checkSlavesStatus(final String masterAddress,
                                final List<String> slaveAddresses,
                                final FutureCallback<Boolean> callback) {
    Preconditions.checkNotNull(masterAddress, "masterAddress cannot be null");
    logger.info("Checking Swarm: {}", masterAddress);

    try {
      String connectionString = createConnectionString(masterAddress);
      swarmClient.getNodeAddressesAsync(connectionString, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> nodes) {
          if (!nodes.contains(masterAddress)) {
            logger.info("Node not registered with Swarm: {}", masterAddress);
            callback.onSuccess(false);
            return;
          }

          try {
            if (slaveAddresses == null || slaveAddresses.size() == 0) {
              // we are only checking the current master
              callback.onSuccess(true);
            } else {
              for (String slaveAddress : slaveAddresses) {
                if (!nodes.contains(slaveAddress)) {
                  logger.info("Slave not registered with Swarm: {}", slaveAddress);
                  callback.onSuccess(false);
                  return;
                }
              }
              callback.onSuccess(true);
            }
          } catch (Exception e) {
            logger.warn("Failed to process Swarm nodes: ", e);
            callback.onFailure(e);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Swarm call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Swarm call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void getSlavesStatus(String serverAddress, final FutureCallback<Set<String>> callback) {

    Preconditions.checkNotNull(serverAddress, "serverAddress cannot be null");
    logger.info("Getting Swarm slaves: {}", serverAddress);

    try {
      String connectionString = createConnectionString(serverAddress);
      swarmClient.getNodeNamesAsync(connectionString, callback);
    } catch (IOException e) {
      logger.warn("Swarm call failed: ", e);
      callback.onFailure(e);
    }
  }

  private static String createConnectionString(String nodeAddress) {
    return "http://" + nodeAddress + ":" + ClusterManagerConstants.Swarm.SWARM_PORT;
  }
}
