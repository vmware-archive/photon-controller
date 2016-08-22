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

import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
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
 * Determines the Status of a Kubernetes Node.
 */
public class KubernetesStatusChecker implements StatusChecker, WorkersStatusChecker {
  private static final Logger logger = LoggerFactory.getLogger(KubernetesStatusChecker.class);

  private KubernetesClient kubernetesClient;

  public KubernetesStatusChecker(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public void checkNodeStatus(final String serverAddress,
                              final FutureCallback<Boolean> callback) {
    Preconditions.checkNotNull(serverAddress, "serverAddress cannot be null");
    logger.info("Checking Kubernetes: {}", serverAddress);

    try {
      String connectionString = createConnectionString(serverAddress);
      kubernetesClient.getNodeAddressesAsync(connectionString, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> nodes) {
          if (!nodes.contains(serverAddress)) {
            logger.info("Node not registered with Kubernetes: {}", serverAddress);
            callback.onSuccess(false);
            return;
          }
          callback.onSuccess(true);
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Kubernetes call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Kubernetes call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void checkWorkersStatus(final String masterAddress,
                                 final List<String> workerAddresses,
                                 final FutureCallback<Boolean> callback) {
    Preconditions.checkNotNull(masterAddress, "masterAddress cannot be null");
    logger.info("Checking Kubernetes: {}", masterAddress);

    try {
      String connectionString = createConnectionString(masterAddress);
      kubernetesClient.getNodeAddressesAsync(connectionString, new FutureCallback<Set<String>>() {
        @Override
        public void onSuccess(@Nullable Set<String> nodes) {
          if (!nodes.contains(masterAddress)) {
            logger.info("Node not registered with Kubernetes: {}", masterAddress);
            callback.onSuccess(false);
            return;
          }

          try {
            if (workerAddresses == null || workerAddresses.size() == 0) {
              // we are only checking the current master
              callback.onSuccess(true);
            } else {
              for (String workerAddress : workerAddresses) {
                if (!nodes.contains(workerAddress)) {
                  logger.info("Worker not registered with Kubernetes: {}", workerAddress);
                  callback.onSuccess(false);
                  return;
                }
              }
              callback.onSuccess(true);
            }
          } catch (Exception e) {
            logger.warn("Failed to process Kubernetes nodes: ", e);
            callback.onFailure(e);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Kubernetes call failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Kubernetes call failed: ", e);
      callback.onSuccess(false);
    }
  }

  @Override
  public void getWorkersStatus(String serverAddress, final FutureCallback<Set<String>> callback) {

    Preconditions.checkNotNull(serverAddress, "serverAddress cannot be null");
    logger.info("Getting Kubernetes workers: {}", serverAddress);

    try {
      String connectionString = createConnectionString(serverAddress);
      kubernetesClient.getNodeNamesAsync(connectionString, callback);
    } catch (IOException e) {
      logger.warn("Kubernetes call failed: ", e);
      callback.onFailure(e);
    }
  }

  private static String createConnectionString(String serverAddress) {
    return "http://" + serverAddress + ":" + ClusterManagerConstants.Kubernetes.API_PORT;
  }
}
