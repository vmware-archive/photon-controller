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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DuplicateHostException;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLoginException;
import com.vmware.photon.controller.apife.exceptions.external.IpAddressInUseException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.common.clients.exceptions.HostExistWithSameAddressException;
import com.vmware.photon.controller.common.clients.exceptions.ManagementVmAddressAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.resource.gen.Host;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand for host creation.
 */
public class HostCreateStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostCreateStepCmd.class);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;
  private static final long STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_CREATE_HOST_TIMEOUT = TimeUnit.MINUTES.toMillis(30);


  private final HostBackend hostBackend;

  private long createHostTimeout;
  private long statusPollInterval;
  private long maxServiceUnavailableCount;

  public HostCreateStepCmd(
      TaskCommand taskCommand, StepBackend stepBackend, StepEntity step, HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;

    this.createHostTimeout = DEFAULT_CREATE_HOST_TIMEOUT;
    this.statusPollInterval = STATUS_POLL_INTERVAL;
    this.maxServiceUnavailableCount = DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<HostEntity> hostList = step.getTransientResourceEntities(null);
    Preconditions.checkArgument(hostList.size() == 1);
    HostEntity hostEntity = hostList.get(0);

    try {
      Host host = buildHost(hostEntity);
      CreateHostResponse createHostResponse = taskCommand.getDeployerClient().createHost(host);
      checkCreateHostStatus(createHostResponse.getOperation_id());
      hostBackend.updateState(hostEntity, HostState.NOT_PROVISIONED);
    } catch (HostExistWithSameAddressException e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw new DuplicateHostException(hostEntity.getAddress());
    } catch (com.vmware.photon.controller.common.clients.exceptions.HostNotFoundException e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw new HostNotFoundException(hostEntity.getId());
    } catch (com.vmware.photon.controller.common.clients.exceptions.InvalidLoginException e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw new InvalidLoginException();
    } catch (ManagementVmAddressAlreadyExistException e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw new IpAddressInUseException(e.getIpAddress());
    } catch (RpcException | InterruptedException e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw new InternalException(e);
    } catch (Exception e) {
      logger.error("Host create failed, mark {} as ERROR", hostEntity);
      hostBackend.updateState(hostEntity, HostState.ERROR);
      throw e;
    }
  }

  private void checkCreateHostStatus(String operationId) throws InterruptedException, RpcException {
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;
    CreateHostStatusResponse response = null;

    while (true) {
      try {
        response = this.taskCommand.getDeployerClient().createHostStatus(operationId);
        switch (response.getStatus().getCode()) {
          case IN_PROGRESS:
            break;
          case FINISHED:
            return;
          case FAILED:
          case CANCELLED:
            logger.error("create host failed {}", response);
            throw new RuntimeException(response.getStatus().getError());
          default:
            logger.error("unexpected create host status {}", response);
            throw new RuntimeException(response.getStatus().getError());
        }

        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= this.maxServiceUnavailableCount) {
          logger.error("checking create host status failed {}", response);
          throw e;
        }
      }

      this.checkCreateHostTimeout(startTime);
      Thread.sleep(this.statusPollInterval);
    }
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected Host buildHost(HostEntity hostEntity) throws RpcException {
    Host host = new Host();
    host.setId(hostEntity.getId());
    host.setAddress(hostEntity.getAddress());
    host.setUsername(hostEntity.getUsername());
    host.setPassword(hostEntity.getPassword());
    host.setAvailabilityZone(hostEntity.getAvailabilityZone());
    host.setMetadata(hostEntity.getMetadata());
    if (StringUtils.isNotBlank(hostEntity.getUsageTags())) {
      host.setUsageTags(UsageTagHelper.deserializeToStringSet(hostEntity.getUsageTags()));
    }

    return host;
  }

  /**
   * Check if the createHost has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  private void checkCreateHostTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= this.createHostTimeout) {
      throw new RuntimeException("Timeout waiting for createHost to complete.");
    }
  }
}
