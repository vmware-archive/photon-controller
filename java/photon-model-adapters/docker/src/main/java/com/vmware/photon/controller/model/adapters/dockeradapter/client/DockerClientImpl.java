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
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;

import java.util.Map;

/**
 * Default implementation of docker client.
 */
public class DockerClientImpl implements DockerClient {

  private ComputeService.ComputeState computeState;
  private DiskService.Disk diskState;
  private com.github.dockerjava.api.DockerClient client;

  /**
   * Sets the compute state instance for the client.
   * @param computeState Represents the compute state instance which contains container related information.
   * @return The docker client instance.
   */
  public DockerClient withComputeState(ComputeService.ComputeState computeState) {
    this.computeState = computeState;
    return this;
  }

  /**
   * Sets the disk state instance for the client.
   * @param diskState Represents the disk state instance which contains image related information.
   * @return The docker client instance.
   */
  public DockerClient withDiskState(DiskService.Disk diskState) {
    this.diskState = diskState;
    return this;
  }

  /**
   * Sets the actual docker client.
   * @param client Represents the actual docker client.
   * @return The docker client instance.
   */
  public DockerClient withClient(com.github.dockerjava.api.DockerClient client) {
    this.client = client;
    return this;
  }

  /**
   * Launches a container instance.
   * @return The result of the launching.
   */
  public RunContainerResponse runContainer() {
    String containerImage = getContainerImage();
    CreateContainerResponse createContainerResponse = createContainer(containerImage);
    String containerId = createContainerResponse.getId();
    startContainer(containerId);
    InspectContainerResponse inspectContainerResponse = inspectContainer(containerId);

    RunContainerResponse response = new RunContainerResponse();
    response.address = inspectContainerResponse.getNetworkSettings().getIpAddress();
    response.primaryMAC = inspectContainerResponse.getConfig().getMacAddress();
    response.powerState = ComputeService.PowerState.ON;
    response.diskStatus = DiskService.DiskStatus.ATTACHED;
    return response;
  }

  /**
   * Deletes a container instance.
   */
  public void deleteContainer() {
    RemoveContainerCmd removeContainerCmd = getClient().removeContainerCmd(computeState.id);
    removeContainerCmd.withForce(true);
    removeContainerCmd.withRemoveVolumes(true);
    removeContainerCmd.exec();
  }

  private String getContainerImage() {
    String sourceImageUriScheme = diskState.sourceImageReference.getScheme();
    if (sourceImageUriScheme.equals("") || sourceImageUriScheme.equals("docker")) {
      return diskState.sourceImageReference.getHost() + "/" + diskState.sourceImageReference.getPath();
    } else {
      throw new IllegalArgumentException("Invalid docker image: " + diskState.sourceImageReference.toString());
    }
  }

  private CreateContainerResponse createContainer(String containerImage) {
    Map<String, String> customProperties = computeState.customProperties;
    CreateContainerCmd createContainerCmd = getClient()
        .createContainerCmd(containerImage)
        .withName(computeState.id);

    if (customProperties.containsKey(DockerInstanceConstants.CP_DOCKER_CREATE_NET)) {
      createContainerCmd.withNetworkMode(customProperties.get(DockerInstanceConstants.CP_DOCKER_CREATE_NET));
    }

    if (customProperties.containsKey(DockerInstanceConstants.CP_DOCKER_CREATE_RESTART_POLICY)) {
      createContainerCmd.withRestartPolicy(RestartPolicy.parse(customProperties.get(
          DockerInstanceConstants.CP_DOCKER_CREATE_RESTART_POLICY)));
    }

    if (customProperties.containsKey(DockerInstanceConstants.CP_DOCKER_CREATE_VOLUME_BINDING)) {
      createContainerCmd.withBinds(Bind.parse(customProperties.get(
          DockerInstanceConstants.CP_DOCKER_CREATE_VOLUME_BINDING)));
    }

    if (customProperties.containsKey(DockerInstanceConstants.CP_DOCKER_CREATE_COMMAND)) {
      createContainerCmd.withCmd(customProperties.get(DockerInstanceConstants.CP_DOCKER_CREATE_COMMAND));
    }

    return createContainerCmd.exec();
  }

  private void startContainer(String containerId) {
    StartContainerCmd startContainerCmd = getClient().startContainerCmd(containerId);
    startContainerCmd.exec();
  }

  private InspectContainerResponse inspectContainer(String containerId) {
    InspectContainerCmd inspectContainerCmd = getClient().inspectContainerCmd(containerId);
    return inspectContainerCmd.exec();
  }

  private com.github.dockerjava.api.DockerClient getClient() {
    if (client == null) {
      String endpoint = computeState.address;
      String port = String.valueOf(DockerInstanceConstants.DEFAULT_DOCKER_PORT);
      if (computeState.customProperties.containsKey(DockerInstanceConstants.CP_DOCKER_PORT)) {
        port = computeState.customProperties.get(DockerInstanceConstants.CP_DOCKER_PORT);
      }

      client = DockerClientBuilder
          .getInstance("http://" + endpoint + ":" + port)
          .withDockerCmdExecFactory(new DockerCmdExecFactoryImpl())
          .build();
    }

    return client;
  }
}
