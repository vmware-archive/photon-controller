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

package com.vmware.photon.controller.model.adapters.dockeradapter;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.dockeradapter.client.DockerClient;
import com.vmware.photon.controller.model.adapters.dockeradapter.client.DockerClientFactory;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeServiceTest;
import com.vmware.photon.controller.model.resources.DiskFactoryService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskServiceTest;
import com.vmware.photon.controller.model.tasks.ComputeSubTaskService;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This class implements tests for the {@link DockerInstanceService} class.
 */
public class DockerInstanceServiceTest {

  @Test
  private void dummy() {
  }

  /**
   * EndToEnd tests.
   */
  public static class EndToEnd extends BaseModelTest {

    /**
     * Mock class of DockerClientFactory.
     */
    public static class DockerInstanceServiceTestHost extends TestHost implements DockerClientFactory {
      public DockerInstanceServiceTestHost(int hostPort, Path sandboxDirectory, Class[] factoryServices)
          throws Throwable {
        super(hostPort, sandboxDirectory, factoryServices);
      }

      public DockerClient build() {
        DockerClient dockerClient = mock(DockerClient.class);

        doReturn(dockerClient).when(dockerClient).withComputeState(any(ComputeService.ComputeState.class));
        doReturn(dockerClient).when(dockerClient).withDiskState(any(DiskService.Disk.class));

        DockerClient.RunContainerResponse response = new DockerClient.RunContainerResponse();
        response.address = "finalAddress";
        response.primaryMAC = "finalPrimaryMAC";
        response.powerState = ComputeService.PowerState.ON;
        response.diskStatus = DiskService.DiskStatus.ATTACHED;
        doReturn(response).when(dockerClient).runContainer();

        doNothing().when(dockerClient).deleteContainer();

        return dockerClient;
      }
    }

    @Override
    protected Class[] getFactoryServices() {
      return new Class[] {
          DockerInstanceService.class,
          ComputeFactoryService.class,
          ComputeDescriptionFactoryService.class,
          DiskFactoryService.class
      };
    }

    @Override
    protected TestHost constructTestHost(int hostPort, Path sandboxDirectory, Class[] factoryServices)
        throws Throwable {
      return new DockerInstanceServiceTestHost(hostPort, sandboxDirectory, factoryServices);
    }

    @Test
    public void testSuccessCreateContainer() throws Throwable {
      List<String> diskDocumentSelfLinks = setupDiskState(1);
      String computeDocumentSelfLink = setupComputeState(diskDocumentSelfLinks);
      String computeSubDocumentSelfLink = setupComputeSubTaskState(computeDocumentSelfLink);

      startDockerInstanceService(ComputeInstanceRequest.InstanceRequestType.CREATE,
          computeSubDocumentSelfLink,
          computeDocumentSelfLink);

      // We use the compute sub task's completions remaining as the finish counter, since we
      // set it to be one more than the number of docker instance task.
      host.waitForServiceState(ComputeSubTaskService.ComputeSubTaskState.class,
          computeSubDocumentSelfLink,
          state -> state.completionsRemaining == 1L);

      ComputeService.ComputeState finalComputeState = host.getServiceSynchronously(
          computeDocumentSelfLink,
          ComputeService.ComputeState.class);
      assertThat(finalComputeState.address, is("finalAddress"));
      assertThat(finalComputeState.primaryMAC, is("finalPrimaryMAC"));
      assertThat(finalComputeState.powerState, is(ComputeService.PowerState.ON));

      DiskService.Disk finalDiskState = host.getServiceSynchronously(
          diskDocumentSelfLinks.get(0),
          DiskService.Disk.class);
      assertThat(finalDiskState.status, is(DiskService.DiskStatus.ATTACHED));
    }

    @Test(dataProvider = "numberOfDiskStates")
    public void testFailureCreateContainerWithInvalidDiskStates(int numberOfDiskStates) throws Throwable {
      List<String> diskDocumentSelfLinks;
      if (numberOfDiskStates == -1) {
        diskDocumentSelfLinks = null;
      } else {
        diskDocumentSelfLinks = setupDiskState(numberOfDiskStates);
      }
      String computeDocumentSelfLink = setupComputeState(diskDocumentSelfLinks);
      String computeSubDocumentSelfLink = setupComputeSubTaskState(computeDocumentSelfLink);

      startDockerInstanceService(ComputeInstanceRequest.InstanceRequestType.CREATE,
          computeSubDocumentSelfLink,
          computeDocumentSelfLink);

      ComputeSubTaskService.ComputeSubTaskState finalComputeSubState = host.waitForServiceState(
          ComputeSubTaskService.ComputeSubTaskState.class,
          computeSubDocumentSelfLink,
          state -> state.completionsRemaining == 1L);

      assertThat(finalComputeSubState.failCount, is(1L));
    }

    @DataProvider(name = "numberOfDiskStates")
    public Object[][] getNumberOfDiskStates() {
      return new Object[][] {
          {-1},
          {0},
          {2}
      };
    }

    @Test
    public void testSuccessDeleteContainer() throws Throwable {
      List<String> diskDocumentSelfLinks = setupDiskState(1);
      String computeDocumentSelfLink = setupComputeState(diskDocumentSelfLinks);
      String computeSubDocumentSelfLink = setupComputeSubTaskState(computeDocumentSelfLink);

      startDockerInstanceService(ComputeInstanceRequest.InstanceRequestType.DELETE,
          computeSubDocumentSelfLink,
          computeDocumentSelfLink);

      host.waitForServiceState(ComputeSubTaskService.ComputeSubTaskState.class,
          computeSubDocumentSelfLink,
          state -> state.completionsRemaining == 1L);

      QueryTask queryResult = host.querySynchronously(host.createDirectQueryTask(
          Utils.buildKind(ComputeService.ComputeState.class),
          ServiceDocument.FIELD_NAME_SELF_LINK,
          computeDocumentSelfLink));
      assertThat(queryResult.results.documentLinks.isEmpty(), is(true));
    }

    private void startDockerInstanceService(ComputeInstanceRequest.InstanceRequestType requestType,
                                            String computeSubDocumentSelfLink,
                                            String computeDocumentSelfLink) throws Throwable {

      ComputeInstanceRequest request = new ComputeInstanceRequest();
      request.requestType = requestType;
      request.provisioningTaskReference = UriUtils.buildUri(host, computeSubDocumentSelfLink);
      request.computeReference = UriUtils.buildUri(host, computeDocumentSelfLink);
      host.patchServiceSynchronously(
          DockerInstanceService.SELF_LINK,
          request);
    }


    private List<String> setupDiskState(int num) throws Throwable {
      List<String> documentSelfLinks = new ArrayList<>();
      for (int i = 0; i < num; ++i) {
        DiskService.Disk diskState = DiskServiceTest.buildValidStartState();
        diskState.status = DiskService.DiskStatus.DETACHED;

        DiskService.Disk diskStartState = host.postServiceSynchronously(
            DiskFactoryService.SELF_LINK,
            diskState,
            DiskService.Disk.class);
        documentSelfLinks.add(diskStartState.documentSelfLink);
      }

      return documentSelfLinks;
    }

    private String setupComputeState(List<String> diskLinks) throws Throwable {
      ComputeDescriptionService.ComputeDescription computeDescription = ComputeDescriptionServiceTest
          .createComputeDescription(host);
      ComputeService.ComputeState computeState = ComputeServiceTest.buildValidStartState(computeDescription);
      computeState.diskLinks = diskLinks;
      computeState.powerState = ComputeService.PowerState.OFF;

      ComputeService.ComputeState computeStartState = host.postServiceSynchronously(
          ComputeFactoryService.SELF_LINK,
          computeState,
          ComputeService.ComputeState.class);

      return computeStartState.documentSelfLink;
    }

    private String setupComputeSubTaskState(String computeLink) throws Throwable {
      ComputeSubTaskService.ComputeSubTaskState subTaskState = new ComputeSubTaskService.ComputeSubTaskState();
      // We set the completionsRemaining larger than the number of docker instance tasks so that it will not patch
      // the parent task, which in our case does not exist.
      subTaskState.completionsRemaining = 2L;
      subTaskState.errorThreshold = 2L;
      String documentSelfLink = UUID.randomUUID().toString();
      host.startServiceAndWait(new ComputeSubTaskService(), documentSelfLink, subTaskState);

      return documentSelfLink;
    }
  }
}
