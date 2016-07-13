/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.commands.steps.VmReleaseIpStepCmd}.
 */
public class VmReleaseIpStepCmdTest {

  private TaskCommand taskCommand;
  private StepEntity step;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
  }

  @Test
  public void testVmIdMissing() throws Throwable {
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();

    try {
      command.execute();
      fail("Should have failed due to missing vm id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), containsString("VM id is not available"));
    }
  }

  @Test
  public void testSuccess() throws Throwable {
    VmReleaseIpStepCmd command = getVmReleaseIpStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
  }

  private VmReleaseIpStepCmd getVmReleaseIpStepCmd() {
    step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmReleaseIpStepCmd cmd = new VmReleaseIpStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }
}
