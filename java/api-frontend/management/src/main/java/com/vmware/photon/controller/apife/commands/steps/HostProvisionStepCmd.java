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
import com.vmware.photon.controller.apife.exceptions.external.HostProvisionFailedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand for host provision.
 */
public class HostProvisionStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostProvisionStepCmd.class);
  private static final long DEFAULT_PROVISION_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
  private static final long STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;
  private final HostBackend hostBackend;

  private long provisionTimeout;
  private long statusPollInterval;
  private long maxServiceUnavailableCount;

  public HostProvisionStepCmd(
      TaskCommand taskCommand, StepBackend stepBackend, StepEntity step, HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;
    this.provisionTimeout = DEFAULT_PROVISION_TIMEOUT;
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
      ProvisionHostResponse response = taskCommand.getDeployerClient().provisionHost(host.getId());
      checkProvisionStatus(response.getOperation_id());
      hostBackend.updateState(host, HostState.READY);
    } catch (Exception ex) {
      logger.error("Host provision failed, mark {} as ERROR", host);
      hostBackend.updateState(host, HostState.ERROR);
      throw ex;
    }
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected void setMaxServiceUnavailableCount(long maxServiceUnavailableCount) {
    this.maxServiceUnavailableCount = maxServiceUnavailableCount;
  }

  @VisibleForTesting
  protected void setStatusPollInterval(long statusPollInterval) {
    this.statusPollInterval = statusPollInterval;
  }

  @VisibleForTesting
  protected void setProvisionTimeout(long provisionTimeout) {
    this.provisionTimeout = provisionTimeout;
  }

  private void checkProvisionStatus(String operationId)
      throws InterruptedException, RpcException, HostProvisionFailedException {
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;
    ProvisionHostStatusResponse response = null;

    while (true) {
      try {
        response = this.taskCommand.getDeployerClient().provisionHostStatus(operationId);
        switch (response.getStatus().getResult()) {
          case IN_PROGRESS:
            break;
          case FINISHED:
            return;
          case FAILED:
          case CANCELLED:
            logger.error("provision failed {}", response);
            throw new HostProvisionFailedException(operationId, response.getStatus().getError());
          default:
            logger.error("unexpected provision status {}", response);
            throw new RuntimeException(response.getStatus().getError());
        }

        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= this.maxServiceUnavailableCount) {
          logger.error("checking provision status failed {}", response);
          throw e;
        }
      }

      this.checkProvisionTimeout(startTime);
      Thread.sleep(this.statusPollInterval);
    }
  }

  /**
   * Check if the provision has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  private void checkProvisionTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= this.provisionTimeout) {
      throw new RuntimeException("Timeout waiting for provision to complete.");
    }
  }
}
