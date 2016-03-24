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

import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig.Spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the ServiceConfigurator.
 */
public class ServiceConfiguratorTest {

  private static final String CONFIG_DIR = "/configurations/";
  private static final String TMP_DIR = "/tmp/configurations/";

  private String configPath;

  @BeforeClass
  public void setUpClass() throws Throwable {
    configPath = ServiceConfiguratorTest.class.getResource(CONFIG_DIR).getPath();
  }

  @Test
  public void testGenerateContainersConfig() throws Exception {
    ServiceConfigurator serviceConfigurator = new ServiceConfigurator();
    ContainersConfig containersConfig = serviceConfigurator.generateContainersConfig(configPath);
    assertThat(containersConfig.getContainerSpecs().size(), is(ContainersConfig.ContainerType.values().length));

    Spec spec = containersConfig.getContainerSpecs().get(ContainersConfig.ContainerType.Deployer.name());
    assertThat(spec.getContainerImage(), is("esxcloud/deployer"));
    assertThat(spec.getPortBindings().size(), is(2));
    assertThat(spec.getVolumeBindings().size(), is(4));
    assertThat(spec.getDynamicParameters().size(), is(9));
  }

  @Test
  public void testApplyDynamicParameters() throws Exception {
    FileUtils.copyDirectory(new File(configPath), new File(TMP_DIR));
    ServiceConfigurator serviceConfigurator = new ServiceConfigurator();
    ContainersConfig containersConfig = serviceConfigurator.generateContainersConfig(configPath);
    serviceConfigurator.applyDynamicParameters(TMP_DIR, ContainersConfig.ContainerType.Deployer, containersConfig
        .getContainerSpecs().get(ContainersConfig.ContainerType.Deployer.name()).getDynamicParameters());
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    DeployerConfig deployerConfig = mapper.readValue(new File(TMP_DIR + "deployer/deployer.yml"), DeployerConfig.class);
    assertThat(deployerConfig.getXenonConfig().getBindAddress(), is("0.0.0.0"));

    Map<String, Object> dynamicParameters = new HashMap<>();
    List<LoadBalancerServer> list = new ArrayList<>();
    list.add(new LoadBalancerServer("server-1", "0.0.0.0"));
    list.add(new LoadBalancerServer("server-2", "1.1.1.1"));
    dynamicParameters.put("MGMT_API_HTTP_SERVERS", list);
    serviceConfigurator.applyDynamicParameters(TMP_DIR, ContainersConfig.ContainerType.LoadBalancer, dynamicParameters);
    File file = new File(TMP_DIR + "haproxy/haproxy.cfg");
    String haproxyCfg = FileUtils.readFileToString(file);
    assertThat(haproxyCfg.contains("server server-1 0.0.0.0 check"), is(true));
    assertThat(haproxyCfg.contains("server server-2 1.1.1.1 check"), is(true));

    dynamicParameters = new HashMap<>();
    List<ZookeeperServer> list1 = new ArrayList<>();
    list1.add(new ZookeeperServer("server.1=0.0.0.0:2888:2888"));
    list1.add(new ZookeeperServer("server.2=1.1.1.1:2888:2888"));
    dynamicParameters.put("ZOOKEEPER_INSTANCES", list1);
    serviceConfigurator.applyDynamicParameters(TMP_DIR, ContainersConfig.ContainerType.Zookeeper, dynamicParameters);
    file = new File(TMP_DIR + "zookeeper/zoo.cfg");
    String zooCfg = FileUtils.readFileToString(file);
    assertThat(zooCfg.contains("server.1=0.0.0.0:2888:2888"), is(true));
    assertThat(zooCfg.contains("server.2=1.1.1.1:2888:2888"), is(true));

    FileUtils.deleteDirectory(new File(TMP_DIR));
  }
}
