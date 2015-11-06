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

package com.vmware.photon.controller.common.zookeeper;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class providing static helper methods for {@link ServiceNode}.
 */
public class ServiceNodeUtils {

  private static final Logger logger = LoggerFactory.getLogger(ServiceNodeUtils.class);

  public static void joinService(
      final ServiceNode serviceNode,
      final long retryIntervalMilliSeconds) {
    joinService(serviceNode, retryIntervalMilliSeconds, null);
  }

  public static void joinService(
      final ServiceNode serviceNode,
      final long retryIntervalMsec,
      final ServiceNodeEventHandler serviceNodeEventHandler) {
    Futures.addCallback(serviceNode.join(), new FutureCallback<ServiceNode.Lease>() {
      @Override
      public void onSuccess(ServiceNode.Lease result) {
        if (serviceNodeEventHandler != null) {
          serviceNodeEventHandler.onJoin();
        }
        handleLeaseExpiration(result, serviceNode, retryIntervalMsec, serviceNodeEventHandler);
      }

      @Override
      public void onFailure(Throwable t) {
        // This could happen when connection to ZK goes away while we were trying to join the service.
        // The proper way to proceed seems to be to just try to join again, rather than exit the process.
        logger.error("Could not acquire service lease", t);
        logger.debug("Sleeping for {} before retrying", retryIntervalMsec);
        try {
          Thread.sleep(retryIntervalMsec);
        } catch (InterruptedException e) {
          logger.error("Failed to sleep between registration retries", e);
        }
        joinService(serviceNode, retryIntervalMsec, serviceNodeEventHandler);
      }
    });
  }

  private static void handleLeaseExpiration(
      ServiceNode.Lease lease,
      final ServiceNode serviceNode,
      final long retryIntervalMsec,
      final ServiceNodeEventHandler serviceNodeEventHandler) {
    Futures.addCallback(lease.getExpirationFuture(), new FutureCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        rejoin();
      }

      @Override
      public void onFailure(Throwable t) {
        // Could happen if the expiration future gets cancelled: in that case
        // we can't reliably detect when we need to expire the connection anymore,
        // so it probably makes sense to leave and attempt to re-join immediately.
        logger.error("The expiration future has failed", t);
        rejoin();
      }

      public void rejoin() {
        if (serviceNodeEventHandler != null) {
          serviceNodeEventHandler.onLeave();
        }
        joinService(serviceNode, retryIntervalMsec, serviceNodeEventHandler);
      }
    });
  }
}
