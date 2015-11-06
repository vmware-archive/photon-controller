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

import com.vmware.photon.controller.clustermanager.clients.MesosClient;
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
 * Determines the Status of a Mesos Node.
 */
public class MesosStatusChecker implements StatusChecker, SlavesStatusChecker {

  private static final Logger logger = LoggerFactory.getLogger(MesosStatusChecker.class);
  private MesosClient mesosClient;

  public MesosStatusChecker(MesosClient mesosClient) {
    this.mesosClient = mesosClient;
  }

  @Override
  public void checkNodeStatus(final String nodeAddress,
                              final FutureCallback<Boolean> callback) {
    try {
      checkStatus(nodeAddress, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String result) {
          callback.onSuccess(true);
        }

        @Override
        public void onFailure(Throwable t) {
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Mesos call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void checkSlavesStatus(final String masterAddress,
                                final List<String> slaveAddresses,
                                final FutureCallback<Boolean> callback) {
    Preconditions.checkNotNull(slaveAddresses, "slaveAddresses cannot be null");
    Preconditions.checkArgument(slaveAddresses.size() > 0, "slaveAddresses cannot be empty");

    try {
      checkStatus(masterAddress, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String leaderConnectionString) {
          try {
            if (slaveAddresses == null || slaveAddresses.size() == 0) {
              // no slaveAddresses - we are only checking the current master
              callback.onSuccess(true);
            } else {
              checkSlaves(leaderConnectionString, slaveAddresses, callback);
            }
          } catch (Throwable t) {
            callback.onFailure(t);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Mesos call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Mesos call failed: ", e);
      callback.onSuccess(false);
    }
  }

  private void checkStatus(String nodeAddress,
                           final FutureCallback<String> callback) throws IOException {
    Preconditions.checkNotNull(nodeAddress, "nodeAddress cannot be null");
    logger.info("Checking Mesos: {}", nodeAddress);

    String connectionString = createConnectionString(nodeAddress);
    mesosClient.getMasterLeader(connectionString, new FutureCallback<String>() {
      @Override
      public void onSuccess(@Nullable String leaderConnectionString) {
        callback.onSuccess(leaderConnectionString);
      }

      @Override
      public void onFailure(Throwable t) {
        logger.warn("Mesos call failed: ", t);
        callback.onFailure(t);
      }
    });
  }

  private void checkSlaves(final String connectionString,
                           final List<String> slaveAddresses,
                           final FutureCallback<Boolean> callback) {
    logger.info("Checking Mesos: {}", connectionString);

    try {
      mesosClient.getNodeAddressesAsync(connectionString, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> nodes) {
          try {
            for (String nodeAddress : slaveAddresses) {
              if (!nodes.contains(nodeAddress)) {
                logger.info("Node not registered with Mesos Master: {}", nodeAddress);
                callback.onSuccess(false);
                return;
              }
            }
            callback.onSuccess(true);
          } catch (Throwable t) {
            logger.warn("Failed to process Mesos nodes: ", t);
            callback.onFailure(t);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Mesos call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Mesos call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void getSlavesStatus(String serverAddress, final FutureCallback<Set<String>> callback) {
    Preconditions.checkNotNull(serverAddress, "serverAddress cannot be null");
    logger.info("Checking Mesos: {}", serverAddress);

    try {
      String connectionString = createConnectionString(serverAddress);
      mesosClient.getMasterLeader(connectionString, new FutureCallback<String>() {
        @Override
        public void onSuccess(@Nullable String leaderConnectionString) {
          try {
            logger.info("Getting Mesos slaves: {}", serverAddress);
            mesosClient.getNodeNamesAsync(leaderConnectionString, callback);
          } catch (Throwable t) {
            callback.onFailure(t);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Mesos call failed: ", t);
          callback.onFailure(t);
        }
      });
    } catch (Exception e) {
      logger.warn("Mesos call failed: ", e);
      callback.onFailure(e);
    }
  }

  private static String createConnectionString(String nodeAddress) {
    return "http://" + nodeAddress + ":" + ClusterManagerConstants.Mesos.MESOS_PORT;
  }
}
