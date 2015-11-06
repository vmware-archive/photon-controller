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

package com.vmware.photon.controller.deployer.dcp;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.helpers.TestHelper;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.fail;

/**
 * Implements tests for {@link ContainersConfig}.
 */
public class ContainersConfigTest {

  private ContainersConfig containersConfig;
  private DeployerConfig deployerConfig;

  @Test
  public void testContainersConfig() throws Exception {
    deployerConfig = ConfigBuilder.build(DeployerConfig.class,
        DcpConfigTest.class.getResource("/config.yml").getPath());
    TestHelper.setContainersConfig(deployerConfig);
    containersConfig = deployerConfig.getContainersConfig();
    assertThat(containersConfig.getContainerSpecs().size(), is(ContainersConfig.ContainerType.values().length));

    ContainersConfig.Spec spec = containersConfig.getContainerSpecs().get(ContainersConfig.ContainerType
        .Deployer.name());
    assertThat(spec.getContainerImage(), is("esxcloud/deployer"));
    assertThat(spec.getPortBindings().size(), is(2));
    assertThat(spec.getPortBindings().get(new Integer(18001)), is(18001));
    assertThat(spec.getVolumeBindings().size(), is(3));
    assertThat(spec.getDynamicParameters().size(), is(11));
  }

  @Test
  public void testMissingContainersConfig() {
    try {
      containersConfig = ConfigBuilder.build(DeployerConfig.class,
          DcpConfigTest.class.getResource("/dcpConfig_invalid.yml").getPath()).getContainersConfig();
      fail();
    } catch (BadConfigException e) {
    }
  }
}
