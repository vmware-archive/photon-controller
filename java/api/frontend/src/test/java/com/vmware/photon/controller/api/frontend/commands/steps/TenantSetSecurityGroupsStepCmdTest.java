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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TenantEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;


/**
 * Tests {@link TenantSetSecurityGroupsStepCmd}.
 */
public class TenantSetSecurityGroupsStepCmdTest {
  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private StepEntity stepEntity;
  private TenantBackend tenantBackend;

  @BeforeMethod
  public void setUp() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    stepEntity = mock(StepEntity.class);
    tenantBackend = mock(TenantBackend.class);
  }

  @Test
  public void testExecuteSuccess() throws Exception {
    TenantEntity tenantEntity = new TenantEntity();
    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);

    TenantSetSecurityGroupsStepCmd cmd =
        new TenantSetSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCheckArgumentFailed() throws Exception {
    doReturn(new ArrayList<TenantEntity>()).when(stepEntity).getTransientResourceEntities(null);

    TenantSetSecurityGroupsStepCmd cmd =
        new TenantSetSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();
  }

  @Test(expectedExceptions = ExternalException.class,
      expectedExceptionsMessageRegExp = "Failed to change security groups")
  public void testThrowExternalException() throws Exception {
    TenantEntity tenantEntity = new TenantEntity();
    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);
    doThrow(new ExternalException("Failed to change security groups"))
        .when(tenantBackend)
        .setSecurityGroups(anyString(), anyObject());

    TenantSetSecurityGroupsStepCmd cmd =
        new TenantSetSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();
  }
}
