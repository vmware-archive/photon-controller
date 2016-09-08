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

import com.vmware.photon.controller.clustermanager.clients.HarborClient;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;

import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Determines the Status of a Harbor Node.
 */
public class HarborStatusChecker implements StatusChecker {

  private static final Logger logger = LoggerFactory.getLogger(HarborStatusChecker.class);
  private HarborClient harborClient;

  public HarborStatusChecker(HarborClient harborClient) {
    this.harborClient = harborClient;
  }

  @Override
  public void checkNodeStatus(final String nodeAddress,
                              final FutureCallback<Boolean> callback) {
    logger.info("Checking Harbor: {}", nodeAddress);

    try {
      String connectionString = createConnectionString(nodeAddress);
      harborClient.checkStatus(connectionString, new FutureCallback<Boolean>() {
        @Override
        public void onSuccess(@Nullable Boolean isReady) {
          try {
            callback.onSuccess(isReady);
          } catch (Throwable t) {
            logger.warn("Harbor node status check failed: ", t);
            callback.onFailure(t);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.warn("Harbor node status check failed: ", t);
          callback.onSuccess(false);
        }
      });
    } catch (Exception e) {
      logger.warn("Harbor node status check failed: ", e);
      callback.onSuccess(false);
    }
  }

  private static String createConnectionString(String serverAddress) {
    return "http://" + serverAddress + ":" + ClusterManagerConstants.Harbor.HARBOR_PORT;
  }
}
