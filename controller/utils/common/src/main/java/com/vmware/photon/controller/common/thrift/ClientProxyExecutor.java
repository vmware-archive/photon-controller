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

import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executor decorator that saves caller tracing context and passes it to callable.
 */
public class ClientProxyExecutor implements ExecutorService {

  private final ExecutorService executor;

  public ClientProxyExecutor(ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public void execute(Runnable command) {
    executor.execute(new ClientProxyRunnable(command));
  }

  /**
   * Everything else just delegates to original executor.
   */

  @Override
  public void shutdown() {
    executor.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executor.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executor.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executor.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return executor.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return executor.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return executor.submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return executor.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return executor.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return executor.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return executor.invokeAny(tasks, timeout, unit);
  }

  /**
   * Inner class used to initialize captured tracing context before running a command.
   */
  private class ClientProxyRunnable implements Runnable {
    protected final Map<String, String> loggerContextMap;  // Pass context map to the new thread
    private final Runnable command;

    public ClientProxyRunnable(Runnable command) {
      loggerContextMap = MDC.getCopyOfContextMap();
      this.command = command;
    }

    @Override
    public void run() {
      if (loggerContextMap != null) {
        MDC.setContextMap(loggerContextMap);
      }
      command.run();
    }
  }

}
