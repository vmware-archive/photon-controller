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

import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.lib.UsageTagHelper;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState;
import com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

/**
 * Tests {@link HostConfigureNsxStepCmd}.
 */
public class HostConfigureNsxStepCmdTest {

  HostConfigureNsxStepCmd command;
  private NsxClientFactory nsxClientFactory;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private DeploymentBackend deploymentBackend;
  private TaskCommand taskCommand;
  private HostEntity host;
  private String hostId = "host1";

  private void setUpCommon() throws Throwable {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostBackend.class);
    deploymentBackend = mock(DeploymentBackend.class);
    nsxClientFactory = mock(NsxClientFactory.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId(hostId);
    host.setState(HostState.NOT_PROVISIONED);
    host.setUsageTags(UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
      add(UsageTag.CLOUD);
    }}));
    step.addResource(host);
    step.createOrUpdateTransientResource(HostConfigureNsxStepCmd.DEPLOYMENT_ID_RESOURCE_KEY, "deployment-1");
    step.createOrUpdateTransientResource(HostConfigureNsxStepCmd.HOST_PASSWORD_RESOURCE_KEY, "password-1");

    command = spy(new HostConfigureNsxStepCmd(taskCommand, stepBackend, step, hostBackend, deploymentBackend));
    when(taskCommand.getNsxClientFactory()).thenReturn(nsxClientFactory);

    doNothing().when(hostBackend).updateNsxConfiguration(any(HostEntity.class), anyString(), anyString());

    DeploymentEntity deploymentEntity = new DeploymentEntity();
    doReturn(deploymentEntity).when(deploymentBackend).findById(anyString());
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link HostProvisionStepCmd#execute()}.
   */
  public class ExecuteTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      NsxClient nsxClientMock = new NsxClientMock.Builder()
          .registerFabricNode(true, "fabricNode")
          .getFabricNodeState(true, FabricNodeState.SUCCESS)
          .getTransportZone(true, "transportZone", "hostSwitch")
          .createTransportNode(true, "transportNode")
          .getTransportNodeState(true, TransportNodeState.SUCCESS)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      command.execute();
      verify(hostBackend, times(1)).updateNsxConfiguration(any(HostEntity.class), anyString(), anyString());
    }
  }
}
