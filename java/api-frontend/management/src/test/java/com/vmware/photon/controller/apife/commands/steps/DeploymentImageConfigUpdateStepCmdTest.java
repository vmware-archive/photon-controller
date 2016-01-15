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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentFailedException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link DeploymentImageConfigUpdateStepCmd}.
 */
public class DeploymentImageConfigUpdateStepCmdTest {

  DeploymentImageConfigUpdateStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentBackend deploymentBackend;
  private HostBackend hostBackend;

  private ImageConfig imageConfig;

  public void setUpCommon() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    deploymentBackend = mock(DeploymentBackend.class);
    hostBackend = mock(HostBackend.class);

    imageConfig = spy(new ImageConfig());
    imageConfig.setUseEsxStore(true);
    imageConfig.setDatastore("ds");
    imageConfig.setEndpoint("10.146.1.1");

    entity = new DeploymentEntity();
    entity.setId("id");
    entity.setOperationId("opid");
    entity.setImageDatastores(Collections.singleton("image-ds"));
    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(entity);

    command = spy(new DeploymentImageConfigUpdateStepCmd(
        taskCommand, stepBackend, step, deploymentBackend, hostBackend, imageConfig));

  }

  /**
   * Dummy test to keep IntelliJ happy.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the execute method.
   */
  public class ExecuteTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testConfigurationComplete() throws Throwable {
      command.execute();
      verify(imageConfig, times(1)).setDatastore(any(String.class));
      verify(imageConfig, times(1)).setEndpoint(any(String.class));
    }

    @Test
    public void testNotUsingEsxDataStore() throws Throwable {
      imageConfig.setUseEsxStore(false);

      command.execute();
      verify(imageConfig, times(1)).setDatastore(any(String.class));
      verify(imageConfig, times(1)).setEndpoint(any(String.class));
    }

    @Test
    public void testSuccessWithPaginatedHosts() throws Throwable {
      imageConfig.setDatastore(null);
      imageConfig.setEndpoint(null);
      int totalHosts = PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE + 5;
      List<Host> hostList = new ArrayList<>();

      for (int i = 0; i < totalHosts; i++) {
        Host host = new Host();
        host.setAddress("10.146.1." + i);
        hostList.add(host);
      }

      ResourceList<Host> hostsPage1 =
          new ResourceList<>(hostList.subList(0, PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      hostsPage1.setNextPageLink("nextLink");

      ResourceList<Host> hostsPage2 =
          new ResourceList<>(hostList.subList(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE, totalHosts));

      doReturn(hostsPage1).when(hostBackend).filterByUsage(UsageTag.MGMT,
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      doReturn(hostsPage2).when(hostBackend).getHostsPage("nextLink");
      hostsPage2.getItems().get(0).setMetadata(ImmutableMap.of(DeploymentImageConfigUpdateStepCmd
          .USE_FOR_IMAGE_UPLOAD_KEY, "value"));

      command.execute();
      verify(imageConfig, times(3)).setDatastore(any(String.class));
      verify(imageConfig, times(3)).setEndpoint(any(String.class));
      Assert.assertTrue(entity.getImageDatastores().contains(imageConfig.getDatastore()));
      Assert.assertEquals(imageConfig.getEndpoint(), "http://" + hostsPage2.getItems().get(0).getAddress());
      Assert.assertEquals(imageConfig.getEndpointHostAddress(), hostsPage2.getItems().get(0).getAddress());
    }

    @Test
    public void testOnlyDataStoreNotSet() throws Throwable {
      imageConfig.setDatastore(null);

      command.execute();
      verify(imageConfig, times(3)).setDatastore(any(String.class));
      verify(imageConfig, times(1)).setEndpoint(any(String.class));
      Assert.assertTrue(entity.getImageDatastores().contains(imageConfig.getDatastore()));
    }

    @Test
    public void testOnlyEndpointNotSet() throws Throwable {
      imageConfig.setEndpoint(null);

      Host host = new Host();
      host.setAddress("10.146.1.12");
      ResourceList<Host> list = new ResourceList<>();
      list.setItems(ImmutableList.of(host));
      doReturn(list).when(hostBackend).filterByUsage(UsageTag.MGMT,
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      command.execute();
      verify(imageConfig, times(1)).setDatastore(any(String.class));
      verify(imageConfig, times(3)).setEndpoint(any(String.class));
      Assert.assertEquals(imageConfig.getEndpoint(), "http://" + host.getAddress());
      Assert.assertEquals(imageConfig.getEndpointHostAddress(), host.getAddress());
    }

    @Test
    public void testEndpointNotSetMarkedHost() throws Throwable {
      imageConfig.setEndpoint(null);

      Host host1 = new Host();
      host1.setAddress("10.146.1.12");
      Host host2 = new Host();
      host2.setAddress("10.146.1.15");
      host2.setMetadata(ImmutableMap.of(DeploymentImageConfigUpdateStepCmd.USE_FOR_IMAGE_UPLOAD_KEY, "true"));
      ResourceList<Host> list = new ResourceList<>();
      list.setItems(ImmutableList.of(host1, host2));
      doReturn(list).when(hostBackend).filterByUsage(UsageTag.MGMT,
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      command.execute();
      verify(imageConfig, times(1)).setDatastore(any(String.class));
      verify(imageConfig, times(3)).setEndpoint(any(String.class));
      Assert.assertEquals(imageConfig.getEndpoint(), "http://" + host2.getAddress());
      Assert.assertEquals(imageConfig.getEndpointHostAddress(), host2.getAddress());
    }

    @Test(expectedExceptions = DeploymentFailedException.class,
        expectedExceptionsMessageRegExp = ".*No management hosts found.$")
    public void testEndpointNotSetAndNoMgmtHosts() throws Throwable {
      imageConfig.setEndpoint(null);

      ResourceList<Host> list = new ResourceList<>();
      list.setItems(ImmutableList.of());
      doReturn(list).when(hostBackend).filterByUsage(UsageTag.MGMT,
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      command.execute();
      verify(imageConfig, times(1)).setDatastore(any(String.class));
      verify(imageConfig, times(3)).setEndpoint(any(String.class));
    }
  }

  /**
   * Tests for markAsFailed method.
   */
  public class MarkAsFailedTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testEntityNotSet() throws Throwable {
      command.markAsFailed(new Throwable());
      verify(deploymentBackend, never()).updateState(any(DeploymentEntity.class), any(DeploymentState.class));
    }

    @Test
    public void testEntitySet() throws Throwable {
      DeploymentEntity entity = new DeploymentEntity();
      command.setEntity(entity);

      command.markAsFailed(new Throwable());
      verify(deploymentBackend).updateState(entity, DeploymentState.ERROR);
    }
  }
}
