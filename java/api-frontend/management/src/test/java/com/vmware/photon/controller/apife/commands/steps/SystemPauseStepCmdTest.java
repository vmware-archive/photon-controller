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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;

import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests {@link SystemPauseStepCmd}.
 */
public class SystemPauseStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private ServiceConfig serviceConfig;

  private StepEntity step;

  private SystemPauseStepCmd command;

  @BeforeMethod
  public void setUp() throws Exception {
    step = new StepEntity();
    step.setId("step-1");
    command = spy(new SystemPauseStepCmd(taskCommand, stepBackend, step, serviceConfig));
  }

  @Test
  public void testSuccess() throws Throwable {
    doNothing().when(serviceConfig).pause();

    command.execute();
    verify(serviceConfig).pause();
    verifyNoMoreInteractions(serviceConfig);
  }

  @Test(expectedExceptions = InternalException.class)
  public void testError() throws Throwable {
    doThrow(new Exception("Error")).when(serviceConfig).pause();
    command.execute();
  }
}
