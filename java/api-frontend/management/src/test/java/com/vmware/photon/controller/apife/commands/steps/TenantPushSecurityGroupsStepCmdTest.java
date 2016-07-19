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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Tests {@link TenantPushSecurityGroupsStepCmd}.
 */
public class TenantPushSecurityGroupsStepCmdTest {
  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private StepEntity stepEntity;
  private TenantBackend tenantBackend;
  private ProjectBackend projectBackend;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    stepEntity = mock(StepEntity.class);
    tenantBackend = mock(TenantBackend.class);
    projectBackend = mock(ProjectBackend.class);
  }

  @Test
  public void testExecuteSuccess() throws Exception {
    List<SecurityGroupEntity> tenantSecurityGroups = new ArrayList<>();
    tenantSecurityGroups.add(new SecurityGroupEntity("sg1", true));
    tenantSecurityGroups.add(new SecurityGroupEntity("sg2", false));

    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId("tenant-id");
    tenantEntity.setSecurityGroups(tenantSecurityGroups);

    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    List<SecurityGroup> projectSecurityGroups = new ArrayList<>();
    projectSecurityGroups.add(new SecurityGroup("sg2", false));
    projectSecurityGroups.add(new SecurityGroup("sg3", false));

    Project project = new Project();
    project.setId("project-id");
    project.setSecurityGroups(projectSecurityGroups);

    List<Project> projects = new ArrayList<>();
    projects.add(project);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenantEntity).when(tenantBackend).findById("tenant-id");

    String fakePageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<Project>(new ArrayList<>(), fakePageLink, null))
        .when(projectBackend)
        .filter("tenant-id", Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(projects)).when(projectBackend).getProjectsPage(fakePageLink);

    TenantPushSecurityGroupsStepCmd cmd =
        new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend, projectBackend);
    cmd.execute();

    verify(projectBackend, times(1)).replaceSecurityGroups(eq("project-id"), anyListOf(SecurityGroup.class));
    verify(stepEntity, times(1)).addWarning(anyObject());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCheckArgumentFailed() throws Exception {
    doReturn(new ArrayList<TenantEntity>()).when(stepEntity).getTransientResourceEntities(null);

    TenantPushSecurityGroupsStepCmd cmd =
        new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend, projectBackend);
    cmd.execute();
  }

  @Test(expectedExceptions = ExternalException.class, expectedExceptionsMessageRegExp = "^Failed to list projects$")
  public void testFailedToGetProjects() throws Exception {
    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId("id");

    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenantEntity).when(tenantBackend).findById("id");

    doThrow(new ExternalException("Failed to list projects"))
        .when(projectBackend)
        .filter("id", Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    TenantPushSecurityGroupsStepCmd cmd =
        new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend, projectBackend);
    cmd.execute();
  }

  @Test
  public void testEmptyProjectsList() throws Exception {
    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId("id");

    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenantEntity).when(tenantBackend).findById("id");
    doReturn(new ResourceList<>(new ArrayList<>())).when(projectBackend).filter("id", Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

    TenantPushSecurityGroupsStepCmd cmd =
        new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend, projectBackend);
    cmd.execute();

    verify(projectBackend, times(0)).replaceSecurityGroups(anyString(), anyListOf(SecurityGroup.class));
  }

  @Test(expectedExceptions = ExternalException.class, expectedExceptionsMessageRegExp = "Failed to update project")
  public void testFailedToUpdate() throws Exception {
    List<SecurityGroupEntity> tenantSecurityGroups = new ArrayList<>();
    tenantSecurityGroups.add(new SecurityGroupEntity("sg1", true));
    tenantSecurityGroups.add(new SecurityGroupEntity("sg2", false));

    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId("tenant-id");
    tenantEntity.setSecurityGroups(tenantSecurityGroups);

    List<TenantEntity> tenantEntityList = new ArrayList<>();
    tenantEntityList.add(tenantEntity);

    List<SecurityGroup> projectSecurityGroups = new ArrayList<>();
    projectSecurityGroups.add(new SecurityGroup("sg2", false));
    projectSecurityGroups.add(new SecurityGroup("sg3", false));

    Project project = new Project();
    project.setId("project-id");
    project.setSecurityGroups(projectSecurityGroups);

    List<Project> projects = new ArrayList<>();
    projects.add(project);

    doReturn(tenantEntityList).when(stepEntity).getTransientResourceEntities(null);
    doReturn(tenantEntity).when(tenantBackend).findById("tenant-id");

    String fakePageLink = UUID.randomUUID().toString();
    doReturn(new ResourceList<Project>(new ArrayList<>(), fakePageLink, null))
        .when(projectBackend)
        .filter("tenant-id", Optional.<String>absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    doReturn(new ResourceList<>(projects)).when(projectBackend).getProjectsPage(fakePageLink);

    doThrow(new ExternalException("Failed to update project"))
        .when(projectBackend)
        .replaceSecurityGroups(eq("project-id"), anyListOf(SecurityGroup.class));

    TenantPushSecurityGroupsStepCmd cmd =
        new TenantPushSecurityGroupsStepCmd(taskCommand, stepBackend, stepEntity, tenantBackend, projectBackend);
    cmd.execute();
  }
}
