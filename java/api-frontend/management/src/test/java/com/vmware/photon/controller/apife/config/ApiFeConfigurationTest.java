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

package com.vmware.photon.controller.apife.config;

import com.vmware.photon.controller.api.Component;

import io.dropwizard.configuration.ConfigurationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Tests {@link ApiFeConfiguration}.
 */
public class ApiFeConfigurationTest {

  private ApiFeConfiguration config;

  @BeforeMethod
  public void setUp() throws Exception {
    config = ConfigurationUtils.parseConfiguration(
        ApiFeConfigurationTest.class.getResource("/config.yml").getPath());
  }

  @Test
  public void testGetApifePort() throws Exception {
    assertThat(config.getApifePort(), is (9000));
  }

  @Test
  public void testGetXenonPort() throws Exception {
    assertThat(config.getXenonPort(), is(19000));
  }

  @Test
  public void testGetBackgroundWorkers() throws Exception {
    assertThat(config.getBackgroundWorkers(), is(500));
  }

  @Test
  public void testGetBackgroundWorkersQueueSize() throws Exception {
    assertThat(config.getBackgroundWorkersQueueSize(), is(200));
  }

  @Test
  public void testBadConfig() {
    try {
      ConfigurationUtils.parseConfiguration(
          ApiFeConfigurationTest.class.getResource("/bad_config.yml").getPath());
      fail();
    } catch (ConfigurationException | IOException e) {
      assertThat(e.getMessage().contains("backgroundWorkers must be between 1 and 2048 (was 0)"),
          is(true));
      assertThat(e.getMessage().contains("backgroundWorkersQueueSize must be between 1 and 2048 (was 3000)"),
          is(true));
    }
  }

  @Test
  public void testStatusConfig() {
    Set expectedComponents = EnumSet.allOf(Component.class);
    assertThat(config.getStatusConfig().getComponents(), is(expectedComponents));
  }

  @Test
  public void testUseXenonBackend() throws Exception {
    assertThat(config.useXenonBackend(), is(true));
  }

  @Test
  public void testMinConfig() throws IOException, ConfigurationException {
    config = ConfigurationUtils.parseConfiguration(
        ApiFeConfigurationTest.class.getResource("/config_min.yml").getPath());

    // When not set, default status config should show status for all components.
    Set expectedComponents = EnumSet.allOf(Component.class);
    assertThat(config.getStatusConfig().getComponents(), is(expectedComponents));
    assertThat(config.useXenonBackend(), is(true));
  }
}
