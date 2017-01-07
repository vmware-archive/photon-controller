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

package com.vmware.photon.controller.api.backend.helpers.mocks;

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationTask;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;

/**
 * A Xenon service that mocks {@link com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationService}.
 */
public class MockSubnetConfigurationService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.DHCPAGENT_ROOT + "/subnet-configuration";

  public static FactoryService createFactory() {
    return FactoryService.create(MockSubnetConfigurationService.class, SubnetConfigurationTask.class);
  }

  public MockSubnetConfigurationService() {
    super(SubnetConfigurationTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, false);
    super.toggleOption(ServiceOption.REPLICATION, false);
    super.toggleOption(ServiceOption.OWNER_SELECTION, false);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    SubnetConfigurationTask s = start.getBody(SubnetConfigurationTask.class);
    if (s.taskState != null && s.taskState.stage == TaskState.TaskStage.STARTED) {
      start.complete();
      return;
    }
    SubnetConfigurationTask currentState = new SubnetConfigurationTask();
    currentState.taskState = new TaskState();
    currentState.taskState.stage = TaskState.TaskStage.STARTED;
    start.setBody(currentState).complete();
  }

  @Override
  public void handlePatch(Operation patch) {
    SubnetConfigurationTask currentState = getState(patch);
    SubnetConfigurationTask patchState = patch.getBody(SubnetConfigurationTask.class);

    currentState.taskState = patchState.taskState;
    patch.complete();
  }
}
