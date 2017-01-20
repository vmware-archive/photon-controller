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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.ServiceBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * StepCommand that kicks off service deletion.
 */
public class ServiceDeleteStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(ServiceDeleteStepCmd.class);
  public static final String SERVICE_ID_RESOURCE_KEY = "service-id";

  private final ServiceBackend serviceBackend;
  private final String serviceId;

  public ServiceDeleteStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                              StepEntity step, ServiceBackend serviceBackend) {
    super(taskCommand, stepBackend, step);
    this.serviceBackend = serviceBackend;

    serviceId = (String) step.getTransientResource(SERVICE_ID_RESOURCE_KEY);
  }

  @Override
  protected void execute() {
    checkNotNull(serviceId, "service-id is not defined in TransientResource");

    logger.info("ServiceDeleteStepCmd started, serviceId={}", serviceId);

    ServiceDeleteTaskState serviceDocument = serviceBackend.getServicesManagerClient()
        .deleteService(serviceId);
    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }

    logger.info("Service deletion initiated: id={}, link={}",
        serviceId, serviceDocument.documentSelfLink);
  }

  @Override
  protected void cleanup() {
  }
}
