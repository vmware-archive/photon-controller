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
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DeploymentPushSecurityGroupsStepCmd}.
 */
public class DeploymentPushSecurityGroupsStepCmdTest {
  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private StepEntity stepEntity;
  private TenantBackend tenantBackend;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    stepEntity = mock(StepEntity.class);
    tenantBackend = mock(TenantBackend.class);
  }

  @Test
  public void testExecuteSuccess() throws Exception {
    DeploymentEntity deploymentEntity = new DeploymentEntity();
    deploymentEntity.setOauthSecurityGroups(ImmutableList.of("adminGroup1", "adminGroup2"));
    List<DeploymentEntity> deploymentEntities = ImmutableList.of(deploymentEntity);

    Tenant tenant = new Tenant();
    tenant.setId("id");
    tenant.setSecurityGroups(ImmutableList.of(new SecurityGroup("adminGroup3", false),
        new SecurityGroup("adminGroup4", false)));
    ResourceList<Tenant> tenants = new ResourceList<>(ImmutableList.of(tenant));

    doReturn(deploymentEntities).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenants).when(tenantBackend).filter(Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    DeploymentPushSecurityGroupsStepCmd cmd =
        new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();

    verify(tenantBackend, times(1)).setSecurityGroups(eq("id"), anyObject());
    verify(stepEntity, times(0)).addWarning(isA(SecurityGroupsAlreadyInheritedException.class));
  }

  @Test
  public void testExecuteSuccessWithPaginatedTenants() throws Exception {
    DeploymentEntity deploymentEntity = new DeploymentEntity();
    deploymentEntity.setOauthSecurityGroups(ImmutableList.of("adminGroup1", "adminGroup2"));
    List<DeploymentEntity> deploymentEntities = ImmutableList.of(deploymentEntity);
    int totalTenants = PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE + 5;
    List<Tenant> tenantList = new ArrayList<>();
    for (int i = 0; i < totalTenants; i++) {
      Tenant tenant = new Tenant();
      tenant.setId("id" + i);
      tenant.setSecurityGroups(ImmutableList.of(new SecurityGroup("adminGroup3", false),
          new SecurityGroup("adminGroup4", false)));
      tenantList.add(tenant);
    }

    ResourceList<Tenant> tenantsPage1 = new ResourceList<>(tenantList.subList(0,
        PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    tenantsPage1.setNextPageLink("nextLink");

    ResourceList<Tenant> tenantsPage2 = new ResourceList<>(tenantList.subList(
        PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE, totalTenants));

    doReturn(deploymentEntities).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenantsPage1).when(tenantBackend).filter(Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    doReturn(tenantsPage2).when(tenantBackend).getPage("nextLink");

    DeploymentPushSecurityGroupsStepCmd cmd =
        new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();

    verify(tenantBackend, times(totalTenants)).setSecurityGroups(anyString(), anyObject());
    verify(stepEntity, times(0)).addWarning(isA(SecurityGroupsAlreadyInheritedException.class));
  }

  @Test
  public void testExecuteSuccessWithWarning() throws Exception {
    DeploymentEntity deploymentEntity = new DeploymentEntity();
    deploymentEntity.setOauthSecurityGroups(ImmutableList.of("adminGroup1", "adminGroup2"));
    List<DeploymentEntity> deploymentEntities = ImmutableList.of(deploymentEntity);

    Tenant tenant = new Tenant();
    tenant.setId("id");
    tenant.setSecurityGroups(ImmutableList.of(new SecurityGroup("adminGroup2", false),
        new SecurityGroup("adminGroup3", false)));
    ResourceList<Tenant> tenants = new ResourceList<>(ImmutableList.of(tenant));

    doReturn(deploymentEntities).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenants).when(tenantBackend).filter(Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    DeploymentPushSecurityGroupsStepCmd cmd =
        new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();

    verify(tenantBackend, times(1)).setSecurityGroups(eq("id"), anyObject());
    verify(stepEntity, times(1)).addWarning(isA(SecurityGroupsAlreadyInheritedException.class));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCheckArgumentFailed() throws Exception {
    doReturn(new ArrayList<DeploymentEntity>()).when(stepEntity).getTransientResourceEntities(null);

    DeploymentPushSecurityGroupsStepCmd cmd =
        new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();
  }

  @Test
  public void testEmptyTenantList() throws Exception {
    List<DeploymentEntity> deploymentEntities = ImmutableList.of(new DeploymentEntity());
    doReturn(deploymentEntities).when(stepEntity).getTransientResourceEntities(null);

    doReturn(new ResourceList<Tenant>(new ArrayList<>())).when(tenantBackend).filter(Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    DeploymentPushSecurityGroupsStepCmd cmd =
        new DeploymentPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend);
    cmd.execute();

    verify(tenantBackend, times(0)).setSecurityGroups(anyString(), anyObject());
  }
}
