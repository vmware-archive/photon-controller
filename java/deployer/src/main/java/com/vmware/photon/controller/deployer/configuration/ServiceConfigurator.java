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

import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.constant.ServiceFileConstants;

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
}
