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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.OutOfThreadPoolWorkerException;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wraps ThreadPoolExecutor to throttle submission size and log uncaught exceptions.
 * <p/>
 * See {@link ThreadPoolExecutor#submit(java.util.concurrent.Callable)}.
 * See {@link ThreadPoolExecutor#afterExecute(Runnable, Throwable)}.
 */
@Singleton
public class TaskCommandExecutorService extends ThreadPoolExecutor {

  private static final Logger logger = LoggerFactory.getLogger(TaskCommandExecutorService.class);

  public TaskCommandExecutorService(int corePoolSize,
                                    int maximumPoolSize,
                                    long keepAliveTime,
                                    TimeUnit unit,
                                    BlockingQueue<Runnable> workQueue,
                                    ThreadFactory threadFactory) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    this.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
  }

  public Future<?> submit(TaskCommand task) throws ExternalException {
    try {
      return super.submit(task);
    } catch (RejectedExecutionException e) {
      logger.error("Fail to acquire ThreadPool worker", e);
      OutOfThreadPoolWorkerException ex = new OutOfThreadPoolWorkerException();
      task.markAllStepsAsFailed(ex);
      throw ex;
    }
  }

  @Override
  protected void afterExecute(Runnable runnable, Throwable throwable) {
    super.afterExecute(runnable, throwable);

    if (throwable == null && runnable instanceof Future<?>) {
      try {
        Future<?> future = (Future<?>) runnable;
        if (future.isDone()) {
          future.get();
        }
      } catch (CancellationException e) {
        throwable = e;
      } catch (ExecutionException e) {
        throwable = e.getCause();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    if (throwable != null) {
      logger.error("Uncaught exception", throwable);
    }
  }
}
