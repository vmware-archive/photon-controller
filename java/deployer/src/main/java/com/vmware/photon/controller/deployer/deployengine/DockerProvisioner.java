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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This class implements the docker wrappers for all docker communication.
 */
public class DockerProvisioner {

  private static final int DOCKER_PORT = 2375;

  private static final long MB_TO_BYTES = 1024 * 1024;

  private final String dockerEndpoint;
  private DockerClient dockerClient;

  public DockerProvisioner(String dockerEndpoint) {
    if (StringUtils.isBlank(dockerEndpoint)) {
      throw new IllegalArgumentException("dockerEndpoint field cannot be null or blank");
    }

    this.dockerEndpoint = dockerEndpoint;
  }

  @VisibleForTesting
  protected DockerClient getDockerClient() {
    if (dockerClient == null) {
      dockerClient = DockerClientBuilder.getInstance("http://" + dockerEndpoint + ":" + Integer.toString(DOCKER_PORT))
          .withDockerCmdExecFactory(new DockerCmdExecFactoryImpl()).build();
    }
    return dockerClient;
  }

  @VisibleForTesting
  protected Ports getPortBindings(Map<Integer, Integer> portBindings) {
    if (portBindings == null || portBindings.isEmpty()) {
      return null;
    }
    Ports containerPortBindings = new Ports();
    for (Integer key : portBindings.keySet()) {
      containerPortBindings.bind(ExposedPort.tcp(key), Ports.Binding(portBindings.get(key)));
    }
    return containerPortBindings;
  }

  @VisibleForTesting
  protected Bind[] getVolumeBindings(Map<String, String> volumeBindings) {
    if (volumeBindings == null || volumeBindings.isEmpty()) {
      return null;
    }

    List<Bind> bindingList = new ArrayList<>();
    for (String key : volumeBindings.keySet()) {
      String[] volumes = volumeBindings.get(key).split(",");
      Arrays.asList(volumes).stream().forEach(volume -> bindingList.add(new Bind(key, new Volume(volume))));
    }
    return bindingList.toArray(new Bind[bindingList.size()]);
  }

  @VisibleForTesting
  protected String[] getEnvironmentVariablesList(Map<String, String> environmentVariables) {
    if (environmentVariables == null || environmentVariables.isEmpty()) {
      return null;
    }
    String[] environmentVariablesList = new String[environmentVariables.keySet().size()];
    int i = 0;
    for (String key : environmentVariables.keySet()) {
      environmentVariablesList[i++] = new String(key + "=" + environmentVariables.get(key));
    }
    return environmentVariablesList;
  }

  @VisibleForTesting
  protected String getImageLoadEndpoint() {
    return "http://" + dockerEndpoint + ":" + Integer.toString(DOCKER_PORT) + "/images/load";
  }

  public void createImageFromTar(String filePath, String imageName) {
    if (StringUtils.isBlank(imageName)) {
      throw new IllegalArgumentException("imageName field cannot be null or blank");
    }
    if (StringUtils.isBlank(filePath)) {
      throw new IllegalArgumentException("filePath field cannot be null or blank");
    }

    File tarFile = new File(filePath);
    try (FileInputStream fileInputStream = new FileInputStream(tarFile)){
      FileChannel in = fileInputStream.getChannel();

      String endpoint = getImageLoadEndpoint();
      URL url = new URL(endpoint);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Accept", "application/json");
      connection.setDoOutput(true);
      WritableByteChannel out = Channels.newChannel(connection.getOutputStream());
      in.transferTo(0, tarFile.length(), out);
      in.close();
      out.close();

      int responseCode = connection.getResponseCode();
      if (!String.valueOf(responseCode).startsWith("2")) {
        throw new RuntimeException("Docker Endpoint cannot load image from tar. Failed with response code " +
            responseCode);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void retagImage(String oldName, String newName, String tag) {
    if (StringUtils.isBlank(oldName)) {
      throw new IllegalArgumentException("oldName field cannot be null or blank");
    }
    if (StringUtils.isBlank(newName)) {
      throw new IllegalArgumentException("newName field cannot be null or blank");
    }
    if (tag == null) {
      tag = "";
    }

    this.getDockerClient().tagImageCmd(oldName, newName, tag).withForce().exec();
  }

  public void removeImage(String imageName) {
    if (StringUtils.isBlank(imageName)) {
      throw new IllegalArgumentException("imageName field cannot be null or blank");
    }

    this.getDockerClient().removeImageCmd(imageName).withForce().exec();
  }

  public String createContainer(String containerName,
                                String containerImage,
                                Integer cpuShares,
                                Long memoryMb,
                                Map<String, String> volumeBindings,
                                Map<Integer, Integer> portBindings,
                                String volumesFrom,
                                Boolean isPrivileged,
                                Map<String, String> environmentVariables,
                                Boolean restart,
                                Boolean useHostNetworkMode,
                                String... command) {
    if (StringUtils.isBlank(containerImage)) {
      throw new IllegalArgumentException("containerImage field cannot be null or blank");
    }
    if (StringUtils.isBlank(containerName)) {
      throw new IllegalArgumentException("containerName field cannot be null or blank");
    }

    // Create container with image and name
    CreateContainerCmd createContainerCmd = this.getDockerClient().createContainerCmd(containerImage);
    createContainerCmd = createContainerCmd.withName(containerName);

    if (cpuShares != null) {
      createContainerCmd = createContainerCmd.withCpuShares(cpuShares);
    }

    // Expose container ports to host
    Ports containerPortBindings = getPortBindings(portBindings);
    if (containerPortBindings != null) {
      Set<ExposedPort> exposedPorts = containerPortBindings.getBindings().keySet();
      createContainerCmd.withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]));
      createContainerCmd = createContainerCmd.withPortBindings(containerPortBindings);
    }

    // Add host volumes to be mounted into the container
    Bind[] containerVolumeBindings = getVolumeBindings(volumeBindings);
    if (containerVolumeBindings != null) {
      createContainerCmd = createContainerCmd.withBinds(containerVolumeBindings);
    }

    // Add environment variables
    String[] environmentVariablesList = getEnvironmentVariablesList(environmentVariables);
    if (environmentVariablesList != null) {
      createContainerCmd = createContainerCmd.withEnv(environmentVariablesList);
    }

    // Attach volumes from container (e.g. To link data container)
    if (volumesFrom != null) {
      createContainerCmd = createContainerCmd.withVolumesFrom(new VolumesFrom(volumesFrom));
    }

    // Run in privileged mode
    if (isPrivileged != null) {
      createContainerCmd = createContainerCmd.withPrivileged(isPrivileged);
    }

    if (restart) {
      createContainerCmd = createContainerCmd.withRestartPolicy(RestartPolicy.alwaysRestart());
    }

    // TODO(ysheng): this is a temporary solution to the port binding issue that Mgmt UI faces
    // when trying to bind port 80 with load balancer. In the long term, we should use a different
    // port for Mgmt UI other than 80.
    if (useHostNetworkMode) {
      createContainerCmd = createContainerCmd.withNetworkMode("host");
    }

    if (containerName.equals(ContainersConfig.ContainerType.Lightwave.name())) {
      //
      // Implement entrypoint and cmd as configuration for containers instead of special-casing Lightwave.
      //
      createContainerCmd = createContainerCmd.withEntrypoint(DeployerDefaults.LIGHTWAVE_ENTRYPOINT);
    } else {
      createContainerCmd = createContainerCmd.withEntrypoint(DeployerDefaults.DEFAULT_ENTRYPOINT);
      if (command != null) {
        createContainerCmd = createContainerCmd.withCmd(command);
      }
    }

    CreateContainerResponse container = createContainerCmd.exec();
    return container.getId();
  }

  public void startContainer(String containerId) {
    if (StringUtils.isBlank(containerId)) {
      throw new IllegalArgumentException("containerId field cannot be null or blank");
    }
    StartContainerCmd startContainerCmd = this.getDockerClient().startContainerCmd(containerId);
    startContainerCmd.exec();
  }

  public String deleteContainer(String containerId, Boolean removeVolumes, Boolean force) {
    if (StringUtils.isBlank(containerId)) {
      throw new IllegalArgumentException("containerId field cannot be null or blank");
    }

    this.getDockerClient().removeContainerCmd(containerId).withRemoveVolumes(removeVolumes).withForce(force).exec();
    return containerId;
  }

  public String launchContainer(String containerName,
                                String containerImage,
                                Integer cpuShares,
                                Long memoryMb,
                                Map<String, String> volumeBindings,
                                Map<Integer, Integer> portBindings,
                                String volumesFrom,
                                Boolean isPrivileged,
                                Map<String, String> environmentVariables,
                                Boolean restart,
                                Boolean useHostNetwork,
                                String... command) {
    if (StringUtils.isBlank(containerImage)) {
      throw new IllegalArgumentException("containerImage field cannot be null or blank");
    }
    if (StringUtils.isBlank(containerName)) {
      throw new IllegalArgumentException("containerName field cannot be null or blank");
    }

    String containerId = createContainer(containerName, containerImage, cpuShares, memoryMb, volumeBindings,
        portBindings, volumesFrom, isPrivileged, environmentVariables, restart, useHostNetwork, command);
    startContainer(containerId);

    return containerId;
  }

  public String getInfo() {
    return this.getDockerClient().infoCmd().exec().toString();
  }

  public void stopContainerMatching(String name) {
    ListContainersCmd listContainersCmd = this.getDockerClient().listContainersCmd();
    Optional.ofNullable(listContainersCmd.exec()).orElse(new ArrayList<>()).stream()
      .filter(c -> Arrays.asList(c.getNames()).stream()
                    .filter(n -> n.toLowerCase().contains(name.toLowerCase()))
                    .count() > 0)
      .forEach(c -> this.getDockerClient().stopContainerCmd(c.getId()));
  }
}
