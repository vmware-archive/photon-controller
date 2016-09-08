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

package com.vmware.photon.controller.common.thrift;

import org.apache.thrift.TApplicationException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncSSLClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async thrift client callback that is used by the {@link ClientPool} to release the underlying client and forward the
 * response to the actual application callback.
 *
 * @param <C> async thrift client type
 * @param <T> response type
 */
class WrappedCallback<C extends TAsyncSSLClient, T> implements AsyncMethodCallback<T> {

  private static final Logger logger = LoggerFactory.getLogger(WrappedCallback.class);

  private final AsyncMethodCallback<T> underlying;
  private final C client;
  private final ClientPool<C> clientPool;

  private boolean clientReleased;

  /**
   * @param underlying     Original callback supplied by caller
   * @param client         Client that called the method
   * @param clientPool     Client pool which the client came from
   * @param tracingManager Tracing manager
   */
  public WrappedCallback(AsyncMethodCallback<T> underlying,
                         C client,
                         ClientPool<C> clientPool) {
    this.underlying = underlying;
    this.client = client;
    this.clientPool = clientPool;
    this.clientReleased = false;
  }

  @Override
  public void onComplete(T response) {
    // The fact that  onComplete got called implies that client is healthy, so we mark it
    // as such before firing the underlying callback.
    releaseClient(true);

    // If onComplete throws an exception, onError will be called by Thrift async client manager.
    // This has to be a last statement in onComplete, so we can make sure we returned client to the pool
    // when our caller-supplied callback fires.
    underlying.onComplete(response);
  }

  @Override
  public void onError(Exception exception) {
    // Thrift async client manager doesn't handle things properly when
    // onError throws an exception. For example, client is not being
    // removed from timeout watch. Thus, one of responsibilities of
    // WrappedCallback is to not let onError throw anything but log instead.
    try {
      logger.error("Releasing client on error", exception);
      releaseClient(exception instanceof TApplicationException);
    } catch (Throwable t) {
      // We still want to call underlying callback onError even if the statements above fail.
      logger.error("Error while running error callback", t);
    }

    try {
      underlying.onError(exception);
    } catch (Throwable t) {
      logger.error("Error while running client error callback", t);
    }
  }

  private void releaseClient(boolean healthy) {
    if (!clientReleased) {
      clientPool.release(client, healthy);
      clientReleased = true;
    }
  }
}
