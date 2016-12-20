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

import org.apache.thrift.async.AsyncMethodCallback;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

/**
 * Sync Handler is a helper for calling async methods synchronously.
 *
 * @param <T> response type
 * @param <C> call type
 */
public class SyncHandler<T, C> implements AsyncMethodCallback<C> {

  private final CountDownLatch done;
  protected T response;
  protected Throwable error;

  public SyncHandler() {
    done = new CountDownLatch(1);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onComplete(Object call) {
    try {
      Method getResultMethod = call.getClass().getMethod("getResult");
      response = (T) getResultMethod.invoke(call);
    } catch (InvocationTargetException e) {
      error = e.getCause();
    } catch (Throwable t) {
      error = t;
    } finally {
      done.countDown();
    }
  }

  @Override
  public void onError(Exception e) {
    error = e;
    done.countDown();
  }

  public T getResponse() throws Throwable {
    if (error != null) {
      throw error;
    }
    return response;
  }

  public Throwable getError() {
    return error;
  }

  public void await() throws InterruptedException {
    done.await();
  }
}
