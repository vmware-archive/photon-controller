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

package com.vmware.photon.controller.api.frontend.commands;

import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

/**
 * Base class for all Commands. From this class are derived both TaskCommand and StepCommand.
 */
public abstract class BaseCommand implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(BaseCommand.class);
  protected final String activityId;
  protected final Map loggerContextMap;

  protected BaseCommand(String activityId) {
    this.activityId = checkNotNull(activityId);
    loggerContextMap = MDC.getCopyOfContextMap();
  }

  public String getActivityId() {
    return activityId;
  }

  @Override
  public void run() {
    if (loggerContextMap != null) {
      MDC.setContextMap(loggerContextMap);
      MDC.put("activity", " [Activity: " + activityId + "]");
    }

    StopWatch stopwatch = new StopWatch();
    stopwatch.start();
    try {
      markAsStarted();
      execute();
      markAsDone();
    } catch (Throwable t) {
      try {
        logger.error("Command execution failed with exception", t);
        markAsFailed(t);
      } catch (TaskNotFoundException e) {
        logger.warn("Could not find task to mark as failed, TaskId=" + e.getTaskId(), e);
      }
    } finally {
      try {
        cleanup();
      } catch (Throwable t) {
        logger.warn("Error cleaning up Command", t);
      }

      stopwatch.stop();
      logger.info("Processed in {}ms", stopwatch.getTime());
    }
  }

  protected abstract void execute() throws ApiFeException, InterruptedException, RpcException;

  protected abstract void cleanup();

  protected abstract void markAsStarted() throws TaskNotFoundException, ConcurrentTaskException;

  protected abstract void markAsDone() throws Throwable;

  protected abstract void markAsFailed(Throwable t) throws TaskNotFoundException;
}
