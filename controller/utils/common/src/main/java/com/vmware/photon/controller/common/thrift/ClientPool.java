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

import com.google.common.util.concurrent.ListenableFuture;
import org.apache.thrift.async.TAsyncSSLClient;

/**
 * Manages a pool of clients ({@link TAsyncClient}). Each client has it's own connection because
 * the protocol is not multiplexed.
 * <p/>
 * It is thread-safe.
 *
 * @param <C> client type
 */
public interface ClientPool<C extends TAsyncSSLClient> {

  /**
   * Acquire a new client.
   *
   * @return promise to acquire a new client.
   */
  ListenableFuture<C> acquire();

  /**
   * Close the pool and cleanup any associated resources.
   */
  void close();

  /**
   * Release a previously acquired client.
   *
   * @param client  acquired client
   * @param healthy true iff the client can be reused
   */
  void release(C client, boolean healthy);

  /**
   * Returns the number of waiters for this pool.
   *
   * @return number of waiters
   */
  int getWaiters();

  /**
   * Returns true if close() was called, otherwise
   * returns false.
   */
  boolean isClosed();
}
