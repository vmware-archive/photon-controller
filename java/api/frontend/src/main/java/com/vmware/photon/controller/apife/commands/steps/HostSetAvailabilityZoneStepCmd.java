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

import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.HostSetAvailabilityZoneFailedException;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for setting host availability zone.
 */
public class HostSetAvailabilityZoneStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostSetAvailabilityZoneStepCmd.class);

  private final HostBackend hostBackend;

  public HostSetAvailabilityZoneStepCmd(TaskCommand taskCommand,
                                        StepBackend stepBackend,
                                        StepEntity step,
                                        HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;
  }

  @Override
  protected void execute() throws HostSetAvailabilityZoneFailedException, HostNotFoundException {

    List<HostEntity> hostList = step.getTransientResourceEntities(Host.KIND);
    Preconditions.checkArgument(hostList.size() == 1);
    HostEntity hostEntity = hostList.get(0);

    hostBackend.updateAvailabilityZone(hostEntity);
  }

  @Override
  protected void cleanup() {

  }
}
