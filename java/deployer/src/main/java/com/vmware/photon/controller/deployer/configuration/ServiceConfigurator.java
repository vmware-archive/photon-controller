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

package com.vmware.photon.controller.deployer.configuration;

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.xenon.constant.ServiceFileConstants;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.xenon.common.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.text.StrSubstitutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements all the utilities needed for configuring the containers.
 */
public class ServiceConfigurator {

  public static final String TCP_PORT_FIREWALL_TEMPLATE = "iptables -I INPUT -p tcp --dport ${port} -j ACCEPT";
  public static final String UDP_PORT_FIREWALL_TEMPLATE =  "iptables -I INPUT -p udp --dport ${port} -j ACCEPT";
  public static final String PORT_SUBSITUTION_KEY = "port";
  public static final String FIREWALL_RULE_FILENAME = "firewall.rules";

  protected static final String ENV_COMMON_ENABLE_AUTH = "ENABLE_AUTH";
  protected static final String ENV_MGMT_API_SWAGGER_LOGIN_URL = "SWAGGER_LOGIN_URL";
  protected static final String ENV_MGMT_API_SWAGGER_LOGOUT_URL = "SWAGGER_LOGOUT_URL";
  protected static final String ENV_MGMT_UI_LOGIN_URL = "MGMT_UI_LOGIN_URL";
  protected static final String ENV_MGMT_UI_LOGOUT_URL = "MGMT_UI_LOGOUT_URL";

  public static final String HAPROXY_CONF_DIR = "/etc/haproxy";
  public static final String LIGHTWAVE_CONF_DIR = "/var/lib/vmware/config";
  private static final String DOCKER_START_COMMAND_FILENAME = "docker-run-commands";

  public ContainersConfig generateContainersConfig(String configDir) {
    ObjectMapper mapper = new ObjectMapper();
    List<ContainersConfig.Spec> containers = new ArrayList<ContainersConfig.Spec>();
    for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {
      File configFile = new File(configDir, ServiceFileConstants.CONTAINER_CONFIG_ROOT_DIRS.get(containerType) +
          ServiceFileConstants.CONTAINER_CONFIG_FILES.get(containerType));
      ContainersConfig.Spec containerConfigSpec = null;
      try {
        containerConfigSpec = mapper.readValue(configFile, ContainersConfig.Spec.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      containerConfigSpec.setType(containerType.name());
      containers.add(containerConfigSpec);
    }

    ContainersConfig containersConfig = new ContainersConfig();
    containersConfig.setContainers(containers);
    return containersConfig;
  }

  public void applyDynamicParameters(String mustacheDir, ContainersConfig.ContainerType containerType,
                                            final Map<String, ?> dynamicParameters) {
    File configDir = new File(mustacheDir, ServiceFileConstants.CONTAINER_CONFIG_ROOT_DIRS.get(containerType));
    Collection<File> files = FileUtils.listFiles(configDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    files.stream().forEach(file -> applyMustacheParameters(file, dynamicParameters));
  }

  public void applyMustacheParameters(File file, Map<String, ?> parameters) {
    try {
      MustacheFactory mustacheFactory = new DefaultMustacheFactory();
      Mustache mustache = mustacheFactory.compile(new InputStreamReader(new FileInputStream(file)), file.getName());
      mustache.execute(new FileWriter(file), parameters).flush();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  // Wrapper over FileUtils for mocking purpose.
  public void copyDirectory(String srcDir, String destDir) throws IOException {
    FileUtils.copyDirectory(new File(srcDir), new File(destDir));
  }

  public void createFirewallRules(Set<Integer> tcpPorts, Set<Integer> udpPorts, String directory) {
    StringBuilder ipTablesRuleBuilder = new StringBuilder();
    for (Integer tcpPort : tcpPorts) {
      ipTablesRuleBuilder.append(
          StrSubstitutor
            .replace(TCP_PORT_FIREWALL_TEMPLATE, Collections.singletonMap(PORT_SUBSITUTION_KEY, tcpPort.toString())))
        .append("\n");
    }
    for (Integer udpPort : udpPorts) {
      ipTablesRuleBuilder.append(
          StrSubstitutor
            .replace(UDP_PORT_FIREWALL_TEMPLATE, Collections.singletonMap(PORT_SUBSITUTION_KEY, udpPort.toString())))
        .append("\n");;
    }
    File fireRuleFile = new File(directory, FIREWALL_RULE_FILENAME);
    try {
      FileUtils.writeStringToFile(fireRuleFile, ipTablesRuleBuilder.toString());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void createDockerContainerStartFile(
      Map<String, ContainerService.State> containerStates,
      Map<String, ContainerTemplateService.State> templateStates,
      DeploymentService.State deploymentState,
      String directory,
      Service service) {

    List<String> containerStartCommands = new ArrayList<>();

    // creating one docker run command for each container that is supposed to be running
    // on a vm
    for (ContainerService.State containerState : containerStates.values()) {
      ContainerTemplateService.State templateState = templateStates.get(containerState.documentSelfLink);

      ContainersConfig.ContainerType containerType =
          ContainersConfig.ContainerType.valueOf(templateState.name);

      // the directory containing all the configuration scripts for the service
      // containerType == service name, e.g. photon-controller
      String hostVolume = ServiceFileConstants.VM_MUSTACHE_DIRECTORY +
          ServiceFileConstants.CONTAINER_CONFIG_ROOT_DIRS.get(containerType);

      // add the directory containing the confiruation to the docker volume bindings
      if (templateState.volumeBindings == null) {
        templateState.volumeBindings = new HashMap<>();
      }
      templateState.volumeBindings.put(hostVolume, ServiceFileConstants.CONTAINER_CONFIG_DIRECTORY);

      String entryPoint = DeployerDefaults.DEFAULT_ENTRYPOINT;
      String commands = DeployerDefaults.DEFAULT_ENTRYPOINT_COMMAND;
      switch (containerType) {
        case LoadBalancer:
          templateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + HAPROXY_CONF_DIR);
          break;
        case Lightwave:
          templateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + LIGHTWAVE_CONF_DIR);
          entryPoint = DeployerDefaults.LIGHTWAVE_ENTRYPOINT;
          commands = "";
          break;
        case PhotonControllerCore:
          // since we are running the lightwave client, which needs the init process to start,
          // we need to start the container using systemd even for the photon-controller container
          entryPoint = DeployerDefaults.LIGHTWAVE_ENTRYPOINT;
          commands = "";
          break;
        default:
          break;
      }

      Map<String, String> environmentVariables = computeEnvironmentVariables(deploymentState, templateState);

      StringBuilder commandBuilder =
          buildDockerCommand(containerState, templateState, entryPoint, commands, environmentVariables);

      containerStartCommands.add(commandBuilder.toString());
    }

    File fireRuleFile = new File(directory, DOCKER_START_COMMAND_FILENAME);

    try {
      FileUtils.writeStringToFile(fireRuleFile, String.join("", containerStartCommands));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, String> computeEnvironmentVariables(
      DeploymentService.State deploymentState,
      ContainerTemplateService.State templateState) {
    Map<String, String> environmentVariables = new HashMap<>();
    if (templateState.environmentVariables != null) {
      environmentVariables.putAll(templateState.environmentVariables);
    }

    // The management UI container is mostly configured through passing in environment variables
    if (deploymentState.oAuthEnabled) {
      environmentVariables.put(ENV_COMMON_ENABLE_AUTH, "true");
      environmentVariables.put(ENV_MGMT_API_SWAGGER_LOGIN_URL, deploymentState.oAuthSwaggerLoginEndpoint);
      environmentVariables.put(ENV_MGMT_API_SWAGGER_LOGOUT_URL, deploymentState.oAuthSwaggerLogoutEndpoint);
      environmentVariables.put(ENV_MGMT_UI_LOGIN_URL, deploymentState.oAuthMgmtUiLoginEndpoint);
      environmentVariables.put(ENV_MGMT_UI_LOGOUT_URL, deploymentState.oAuthMgmtUiLogoutEndpoint);
    }
    return environmentVariables;
  }

  public StringBuilder buildDockerCommand(
      ContainerService.State containerState,
      ContainerTemplateService.State templateState,
      String entryPoint,
      String commands,
      Map<String, String> environmentVariables) {
    // create docker run command
    StringBuilder commandBuilder = new StringBuilder();
    commandBuilder.append("docker run -d ");
    // add tcp ports
    for (Map.Entry<Integer, Integer> portEntry : templateState.portBindings.entrySet()) {
      // -p hostPort:containerPort
      commandBuilder.append("-p " + portEntry.getValue() + ":" + portEntry.getKey() + "/tcp ");
    }
    // add udp ports
    for (Map.Entry<Integer, Integer> portEntry : templateState.udpPortBindings.entrySet()) {
      // -p hostPort:containerPort
      commandBuilder.append("-p " + portEntry.getValue() + ":" + portEntry.getKey() + "/udp ");
    }
    // add volumes
    for (Map.Entry<String, String> volumeEntry : templateState.volumeBindings.entrySet()) {
      // this supports mapping a hostfolder to multiple container folders
      for (String containerVolume : volumeEntry.getValue().split(",")) {
        commandBuilder.append("-v " + volumeEntry.getKey() + ":" + containerVolume + " ");
      }
    }
    // add environment variables
    for (Map.Entry<String, String> envEntry : environmentVariables.entrySet()) {
      commandBuilder.append("-e \"" + envEntry.getKey() + "=" + envEntry.getValue() + "\" ");
    }
    // add privileged
    if (templateState.isPrivileged) {
      commandBuilder.append("--privileged=true ");
    }
    // add host networking
    if (templateState.useHostNetwork) {
      commandBuilder.append("--net=\"host\" ");
    }
    // cpu shares
    if (containerState.cpuShares != null) {
      commandBuilder.append("--cpu-shares=" + containerState.cpuShares + " ");
    }
    // memory shares - set it only for non PhotonControllerCore containers. This is to alleviate memory leak issues
    // that we are seeing in v1.1.0 where cgroup kills the java process running inside the PhotonControllerCore
    // container due to the container using all of its memory. This happened when PhotonControllerCore container
    // was restricted to 5GB.
    if (!templateState.name.equals(ContainersConfig.ContainerType.PhotonControllerCore.name())) {
      if (containerState.memoryMb != null) {
        commandBuilder.append("--memory=" + containerState.memoryMb + "m ");
      }
    }
    // Attach volumes from container (e.g. To link data container)
    if (templateState.volumesFrom != null) {
      commandBuilder.append("--volumes-from=\"" + templateState.volumesFrom + "\" ");
    }
    // make container auto restart
    commandBuilder.append("--restart=always ");
    // add name
    commandBuilder.append("--name=" + templateState.name + " ");
    // add endpoint
    commandBuilder.append("--entrypoint=\"" + entryPoint + "\" ");
    // volumes from
    // add image, contains the version number
    commandBuilder.append(templateState.containerImage + " ");

    commandBuilder.append(commands + "\n");
    return commandBuilder;
  }
}
