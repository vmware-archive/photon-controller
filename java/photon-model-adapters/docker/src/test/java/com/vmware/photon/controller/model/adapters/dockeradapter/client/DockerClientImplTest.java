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

package com.vmware.photon.controller.model.adapters.dockeradapter.client;

import com.vmware.photon.controller.model.adapters.dockeradapter.DockerInstanceConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.RestartPolicy;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.HashMap;

/**
 * This class implements tests for the {@link DockerClientImpl} class.
 */
public class DockerClientImplTest {
  private static ComputeService.ComputeState buildComputeState() {
    ComputeService.ComputeState state = new ComputeService.ComputeState();
    state.id = "computeId";
    state.address = "1.2.3.4";
    state.customProperties = new HashMap<>();
    state.customProperties.put(DockerInstanceConstants.CP_DOCKER_CREATE_NET, "host");
    state.customProperties.put(DockerInstanceConstants.CP_DOCKER_CREATE_RESTART_POLICY, "no");
    state.customProperties.put(DockerInstanceConstants.CP_DOCKER_CREATE_VOLUME_BINDING, "/src:/dest");
    state.customProperties.put(DockerInstanceConstants.CP_DOCKER_CREATE_COMMAND, "ls -lt");

    return state;
  }

  private static DiskService.Disk buildDiskState() {
    DiskService.Disk state = new DiskService.Disk();
    state.sourceImageReference = URI.create("docker://foo:bar");

    return state;
  }

  @Test
  public void testRunContainer() {
    DockerClient dockerClient = new DockerClientImpl()
        .withClient(mockClient())
        .withComputeState(buildComputeState())
        .withDiskState(buildDiskState());

    DockerClient.RunContainerResponse response = dockerClient.runContainer();
    assertThat(response.address, is("ipAddress"));
    assertThat(response.primaryMAC, is("macAddress"));
    assertThat(response.powerState, is(ComputeService.PowerState.ON));
    assertThat(response.diskStatus, is(DiskService.DiskStatus.ATTACHED));
  }

  @Test
  public void testDeleteContainer() {
    DockerClient dockerClient = new DockerClientImpl()
        .withClient(mockClient())
        .withComputeState(buildComputeState());

    dockerClient.deleteContainer();
  }

  private com.github.dockerjava.api.DockerClient mockClient() {
    com.github.dockerjava.api.DockerClient client = mock(com.github.dockerjava.api.DockerClient.class);

    CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
    CreateContainerResponse createContainerResponse = new CreateContainerResponse();
    createContainerResponse.setId("computeId");
    doReturn(createContainerCmd).when(createContainerCmd).withName(any(String.class));
    doReturn(createContainerCmd).when(createContainerCmd).withNetworkMode(any(String.class));
    doReturn(createContainerCmd).when(createContainerCmd).withRestartPolicy(any(RestartPolicy.class));
    doReturn(createContainerCmd).when(createContainerCmd).withBinds(any(Bind.class));
    doReturn(createContainerCmd).when(createContainerCmd).withCmd(any(String.class));
    doReturn(createContainerResponse).when(createContainerCmd).exec();
    doReturn(createContainerCmd).when(client).createContainerCmd(any(String.class));

    StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
    doReturn(null).when(startContainerCmd).exec();
    doReturn(startContainerCmd).when(client).startContainerCmd(any(String.class));

    InspectContainerCmd inspectContainerCmd = mock(InspectContainerCmd.class);
    InspectContainerResponse inspectContainerResponse = mock(InspectContainerResponse.class);
    InspectContainerResponse.NetworkSettings networkSettings = mock(InspectContainerResponse.NetworkSettings.class);
    doReturn("ipAddress").when(networkSettings).getIpAddress();
    doReturn(networkSettings).when(inspectContainerResponse).getNetworkSettings();
    ContainerConfig containerConfig = mock(ContainerConfig.class);
    doReturn("macAddress").when(containerConfig).getMacAddress();
    doReturn(containerConfig).when(inspectContainerResponse).getConfig();
    doReturn(inspectContainerResponse).when(inspectContainerCmd).exec();
    doReturn(inspectContainerCmd).when(client).inspectContainerCmd(any(String.class));

    RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
    doReturn(removeContainerCmd).when(removeContainerCmd).withForce(any(Boolean.class));
    doReturn(removeContainerCmd).when(removeContainerCmd).withRemoveVolumes(any(Boolean.class));
    doReturn(removeContainerCmd).when(client).removeContainerCmd(any(String.class));

    return client;
  }
}
