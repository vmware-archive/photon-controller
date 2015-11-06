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

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostDeprovisionFailedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand for host deprovision.
 */
public class HostDeprovisionStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostDeprovisionStepCmd.class);
  private static final long DEFAULT_DEPROVISION_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
  private static final long STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;
  private final HostBackend hostBackend;

  private long deprovisionTimeout;
  private long statusPollInterval;
  private long maxServiceUnavailableCount;

  public HostDeprovisionStepCmd(
      TaskCommand taskCommand, StepBackend stepBackend, StepEntity step, HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;
    this.deprovisionTimeout = DEFAULT_DEPROVISION_TIMEOUT;
    this.statusPollInterval = STATUS_POLL_INTERVAL;
    this.maxServiceUnavailableCount = DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<HostEntity> hostList = step.getTransientResourceEntities(Host.KIND);
    Preconditions.checkArgument(hostList.size() == 1);
    HostEntity host = hostList.get(0);

    try {
      logger.info("Calling deployer to provision host {}", host);
      DeprovisionHostResponse response = taskCommand.getDeployerClient().deprovisionHost(host.getId());
      checkDeprovisionStatus(response.getOperation_id());
      hostBackend.updateState(host, HostState.NOT_PROVISIONED);
    } catch (Exception ex) {
      logger.error("Host deprovision failed, mark {} as ERROR", host);
      if (host.getState() == HostState.ERROR) {
        return;
      }
      hostBackend.updateState(host, HostState.ERROR);
      throw ex;
    }
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected void setDeprovisionTimeout(long deprovisionTimeout) {
    this.deprovisionTimeout = deprovisionTimeout;
  }

  @VisibleForTesting
  protected void setStatusPollInterval(long statusPollInterval) {
    this.statusPollInterval = statusPollInterval;
  }

  @VisibleForTesting
  protected void setMaxServiceUnavailableCount(long maxServiceUnavailableCount) {
    this.maxServiceUnavailableCount = maxServiceUnavailableCount;
  }

  private void checkDeprovisionStatus(String operationId)
      throws InterruptedException, RpcException, HostDeprovisionFailedException {
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;
    DeprovisionHostStatusResponse response = null;

    while (true) {
      try {
        response = this.taskCommand.getDeployerClient().deprovisionHostStatus(operationId);
        switch (response.getStatus().getResult()) {
          case IN_PROGRESS:
            break;
          case FINISHED:
            return;
          case FAILED:
          case CANCELLED:
            logger.error("deprovision failed {}", response);
            throw new HostDeprovisionFailedException(operationId, response.getStatus().getError());
          default:
            logger.error("unexpected deprovision status {}", response);
            throw new RuntimeException(response.getStatus().getError());
        }

        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= this.maxServiceUnavailableCount) {
          logger.error("checking deprovision status failed {}", response);
          throw e;
        }
      }

      this.checkDeprovisionTimeout(startTime);
      Thread.sleep(this.statusPollInterval);
    }
  }

  /**
   * Check if the provision has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  private void checkDeprovisionTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= this.deprovisionTimeout) {
      throw new RuntimeException("Timeout waiting for deprovision to complete.");
    }
  }
}
