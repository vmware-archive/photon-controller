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

import com.google.common.util.concurrent.SettableFuture;
import org.apache.thrift.async.TAsyncSSLClient;

import java.util.concurrent.ScheduledFuture;

/**
 * Holds the client and timeout futures.
 *
 * @param <C> thrift async client type
 */
class Promise<C extends TAsyncSSLClient> {
  private SettableFuture<C> future;
  private ScheduledFuture<Void> timeout;
  private boolean invoked;

  Promise(SettableFuture<C> future) {
    this.future = future;
  }

  public synchronized void setTimeout(ScheduledFuture<Void> timeout) {
    this.timeout = timeout;
  }

  public synchronized boolean set(C value) {
    if (timeout != null) {
      timeout.cancel(false);
    }

    if (this.invoked) {
      // If Promise#setException() is already invoked, skip set()
      return false;
    }

    this.invoked = true;
    return future.set(value);
  }

  public synchronized boolean setException(Throwable throwable) {
    if (timeout != null) {
      // If setException() is called not by Promise#setTimeout, cancel timeout.
      // If setException() is called by Promise#setTimeout, timeout.cancel(false) return false.
      timeout.cancel(false);
    }

    if (this.invoked) {
      // If Promise#set() is already invoked, skip setException()
      return false;
    }

    this.invoked = true;
    return future.setException(throwable);
  }

  public synchronized boolean isDone() {
    return future.isDone();
  }

}
