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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.TaskState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.TimeUnit;

/**
 * StepCommand that monitors the status of a dcp task.
 */
public class ClusterTaskStatusStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(ClusterTaskStatusStepCmd.class);
  public static final String REMOTE_TASK_LINK_RESOURCE_KEY = "remote-task-link";

  private static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
  private static final long DEFAULT_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(5);
  private static final long DEFAULT_SERVICE_UNAVAILABLE_MAX_COUNT = 100;

  private long timeout;
  private long pollInterval;
  private long documentNotFoundMaxCount;
  private long documentNotFoundOccurrence;
  private final String remoteTaskLink;
  private final int targetSubStage;
  private final ClusterTaskStatusPoller clusterTaskStatusPoller;

  interface ClusterTaskStatusPoller {
    TaskState poll(String taskLink)
        throws DocumentNotFoundException, TaskNotFoundException;

    int getTargetSubStage(Operation op);

    int getSubStage(TaskState taskState);
  }

  public ClusterTaskStatusStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                                  StepEntity step,
                                  ClusterTaskStatusPoller clusterTaskStatusPoller) {
    super(taskCommand, stepBackend, step);
    this.timeout = DEFAULT_TIMEOUT;
    this.pollInterval = DEFAULT_POLL_INTERVAL;
    this.documentNotFoundMaxCount = DEFAULT_SERVICE_UNAVAILABLE_MAX_COUNT;
    this.clusterTaskStatusPoller = Preconditions.checkNotNull(clusterTaskStatusPoller);

    // get the targetSubStage from Operation
    targetSubStage = clusterTaskStatusPoller.getTargetSubStage(step.getOperation());

    // get remoteTaskLink and recent status past from previous step
    remoteTaskLink = (String) step.getTransientResource(REMOTE_TASK_LINK_RESOURCE_KEY);
  }

  @VisibleForTesting
  protected void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  @VisibleForTesting
  protected void setPollInterval(long pollInterval) {
    this.pollInterval = pollInterval;
  }

  @VisibleForTesting
  protected void setDocumentNotFoundMaxCount(long documentNotFoundMaxCount) {
    this.documentNotFoundMaxCount = documentNotFoundMaxCount;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    checkNotNull(remoteTaskLink, "remote-task-link is not defined in TransientResource");

    logger.info("ClusterTaskStatusStepCmd started, operation={}, remoteTaskLink={}",
        step.getOperation(), remoteTaskLink);

    // Poll remote task status until currentSubStage is completed.
    long startTime = System.currentTimeMillis();
    while (!checkSubStageCompletion()) {
      if (System.currentTimeMillis() - startTime >= timeout) {
        throw new RuntimeException("Cluster task did not complete in timely fashion.");
      }
      Thread.sleep(pollInterval);
    }
  }

  @Override
  protected void cleanup() {
  }

  /**
   * this method returns true if targetSubStage completed successfully, returns false
   * if targetSubStage is in progress, and throws exception if encounter failures.
   */
  private boolean checkSubStageCompletion() throws ExternalException {
    // Call service to get task status
    TaskState taskState;
    try {
      taskState = clusterTaskStatusPoller.poll(remoteTaskLink);
    } catch (DocumentNotFoundException ex) {
      documentNotFoundOccurrence++;
      if (documentNotFoundOccurrence < this.documentNotFoundMaxCount) {
        // Ignore temporary service unavailable failures and retry
        return false;
      }
      // Service is unavailable for an extended period of time, stop retry
      logger.error("Service is unavailable for an extended period of time.", ex);
      throw new ExternalException(ex);
    }

    // Successfully get status, read the payload
    documentNotFoundOccurrence = 0;

    switch (taskState.stage) {
      case STARTED:
        // If currentSubStage is later than targetSubStage, we can consider targetSubStage
        // is done, and exit this step command.
        return clusterTaskStatusPoller.getSubStage(taskState) > targetSubStage;
      case FINISHED:
        // The overall task has finished, we can consider targetSubStage is done,
        // and exit this step command.
        return true;
      case FAILED:
        // If task has failed, use exception to exit.
        String failure = (taskState.failure != null) ?
            taskState.failure.message : "Task failed.";
        throw new ExternalException(failure);
      case CANCELLED:
        // If backend task is cancelled, use exception to exit.
        throw new ExternalException("Task was cancelled.");
      default:
        throw new IllegalStateException("Unexpected stage: " + taskState.stage);
    }
  }
}
