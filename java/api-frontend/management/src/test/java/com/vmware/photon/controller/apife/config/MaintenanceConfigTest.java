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

import com.codahale.dropwizard.util.Duration;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

import java.util.Objects;

/**
 * Tests {@link MaintenanceConfig}.
 */
public class MaintenanceConfigTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link MaintenanceConfig#taskExpirationThreshold}.
   */
  public class TaskExpirationThresholdTest {

    @DataProvider(name = "config")
    public Object[][] getConfig() {
      return new Object[][]{
          {"/config.yml", Duration.minutes(3)},
          {"/config_min.yml", MaintenanceConfig.DEFAULT_TASK_EXPIRATION_THRESHOLD},
      };
    }

    @Test(dataProvider = "config")
    public void testConfig(String configFile, Duration taskExpirationThreshold)
        throws Exception {
      MaintenanceConfig config = ConfigurationUtils.parseConfiguration(
          MaintenanceConfigTest.class.getResource(configFile).getPath()).getMaintenanceConfig();
      assertTrue(Objects.equals(config.getTaskExpirationThreshold(), taskExpirationThreshold));
    }
  }

  /**
   * Tests {@link MaintenanceConfig#taskExpirationScanInterval}.
   */
  public class TaskExpirationScanIntervalTest {

    @DataProvider(name = "config")
    public Object[][] getConfig() {
      return new Object[][]{
          {"/config.yml", Duration.minutes(3)},
          {"/config_min.yml", MaintenanceConfig.DEFAULT_TASK_EXPIRATION_SCAN_INTERVAL},
      };
    }

    @Test(dataProvider = "config")
    public void testConfig(String configFile, Duration taskExpirationScanInterval)
        throws Exception {
      MaintenanceConfig config = ConfigurationUtils.parseConfiguration(
          MaintenanceConfigTest.class.getResource(configFile).getPath()).getMaintenanceConfig();
      assertTrue(Objects.equals(config.getTaskExpirationScanInterval(), taskExpirationScanInterval));
    }
  }
}
