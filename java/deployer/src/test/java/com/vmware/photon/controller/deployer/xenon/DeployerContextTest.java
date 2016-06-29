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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * This class implements tests for the {@link DeployerContext} class.
 */
public class DeployerContextTest {

  private DeployerContext deployerContext;

  private void createConfigFile(File scriptFile, String content) throws IOException {
    scriptFile.createNewFile();
    FileUtils.writeStringToFile(scriptFile, content);
  }

  /**
   * Dummy test case to make IntelliJ recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the default test configuration file.
   */
  public class TestDefaultConfig {

    @BeforeClass
    public void setUpClass() throws BadConfigException {
      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          DeployerContextTest.class.getResource("/config.yml").getPath()).getDeployerContext();
    }

    @AfterClass
    public void tearDownClass() {
      deployerContext = null;
    }

    @Test
    public void testEnableSysLog() {
      assertThat(deployerContext.getEnableSyslog(), is(true));
    }

    @Test
    public void testScriptDirectory() {
      assertThat(deployerContext.getScriptDirectory(), is("/tmp/deployAgent/scripts"));
    }

    @Test
    public void testScriptLogDirectory() {
      assertThat(deployerContext.getScriptLogDirectory(), is("/tmp/deployAgent/logs"));
    }

    @Test
    public void testConfigDirectory() {
      assertThat(deployerContext.getConfigDirectory(), is("/tmp/deployAgent/configurations"));
    }

    @Test
    public void testSysLogEndpoint() {
      assertThat(deployerContext.getSyslogEndpoint(), is("syslog endpoint"));
    }

    @Test
    public void testVibDirectory() {
      assertThat(deployerContext.getVibDirectory(), is("/tmp/deployAgent/vibs"));
    }

    @Test
    public void testSharedSecret() {
      assertThat(deployerContext.getSharedSecret(), is("shared-secret"));
    }

    @Test
    public void testDeployerDefaultOverrides() {
      assertThat(deployerContext.getCorePoolSize(), is(17));
      assertThat(deployerContext.getMaximumPoolSize(), is(17));
      assertThat(deployerContext.getKeepAliveTime(), is(17L));
      assertThat(deployerContext.getTaskPollDelay(), is(50));
      assertThat(deployerContext.getMaxMemoryGb(), is(64));
      assertThat(deployerContext.getMaxVmCount(), is(20));
      assertThat(deployerContext.getDeployerPort(), is(18000));
      assertThat(deployerContext.getXenonRetryCount(), is(17));
      assertThat(deployerContext.getXenonRetryIntervalMs(), is(50));
      assertThat(deployerContext.getScriptTimeoutSec(), is(17));
      assertThat(deployerContext.getPollingIntervalMs(), is(50));
      assertThat(deployerContext.getWaitForServiceMaxRetryCount(), is(17));
    }
  }

  /**
   * This class implements tests for the minimal test configuration file.
   */
  public class TestMinimalConfig {

    @BeforeClass
    public void setUpClass() throws BadConfigException {
      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          DeployerContextTest.class.getResource("/config_min.yml").getPath()).getDeployerContext();
    }

    @AfterClass
    public void tearDownClass() {
      deployerContext = null;
    }

    @Test
    public void testEnableSysLog() {
      assertThat(deployerContext.getEnableSyslog(), is(false));
    }

    @Test
    public void testScriptDirectory() {
      assertThat(deployerContext.getScriptDirectory(), is("/tmp/deployAgent/scripts"));
    }

    @Test
    public void testScriptLogDirectory() {
      assertThat(deployerContext.getScriptLogDirectory(), is("/tmp/deployAgent/logs"));
    }

    @Test
    public void testConfigDirectory() {
      assertThat(deployerContext.getConfigDirectory(), is("/tmp/deployAgent/configurations"));
    }

    @Test
    public void testSysLogEndpoint() {
      assertThat(deployerContext.getSyslogEndpoint(), nullValue());
    }

    @Test
    public void testVibDirectory() {
      assertThat(deployerContext.getVibDirectory(), is("/tmp/deployAgent/vibs"));
    }

    @Test
    public void testSharedSecret() {
      UUID uuid = UUID.fromString(deployerContext.getSharedSecret());
      assertThat(deployerContext.getSharedSecret(), is(uuid.toString()));
    }

    @Test
    public void testDeployerDefaults() {
      assertThat(deployerContext.getCorePoolSize(), is(DeployerDefaults.CORE_POOL_SIZE));
      assertThat(deployerContext.getMaximumPoolSize(), is(DeployerDefaults.MAXIMUM_POOL_SIZE));
      assertThat(deployerContext.getKeepAliveTime(), is(DeployerDefaults.KEEP_ALIVE_TIME));
      assertThat(deployerContext.getTaskPollDelay(), is(DeployerDefaults.DEFAULT_TASK_POLL_DELAY));
      assertThat(deployerContext.getMaxMemoryGb(), is(DeployerDefaults.DEFAULT_MAX_MEMORY_GB));
      assertThat(deployerContext.getMaxVmCount(), is(DeployerDefaults.DEFAULT_MAX_VM_COUNT));
      assertThat(deployerContext.getDeployerPort(), is(DeployerDefaults.DEPLOYER_PORT_NUMBER));
      assertThat(deployerContext.getXenonRetryCount(), is(DeployerDefaults.DEFAULT_XENON_RETRY_COUNT));
      assertThat(deployerContext.getXenonRetryIntervalMs(),
          is(DeployerDefaults.DEFAULT_XENON_RETRY_INTERVAL_MILLISECOND));
      assertThat(deployerContext.getScriptTimeoutSec(), is(DeployerDefaults.SCRIPT_TIMEOUT_IN_SECONDS));
      assertThat(deployerContext.getPollingIntervalMs(), is(DeployerDefaults.DEFAULT_POLLING_INTERVAL_MILLISECOND));
      assertThat(deployerContext.getWaitForServiceMaxRetryCount(),
          is(DeployerDefaults.DEFAULT_WAIT_FOR_SERVICE_MAX_RETRY_COUNT));
    }
  }

  /**
   * This class implements tests for various invalid configuration files.
   */
  public class TestInvalidConfig {

    private final File storageDirectory = new File("/tmp/deployerContext");

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() throws IOException {
      storageDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @Test
    public void testMissingEnableSysLog() throws Exception {

      String configFileContents = "" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing enableSysLog field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("enableSyslog may not be null (was null)"));
      }
    }

    @Test
    public void testMissingScriptDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing scriptDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("scriptDirectory may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankScriptDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory:\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing scriptDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("scriptDirectory may not be empty (was )"));
      }
    }

    @Test
    public void testMissingScriptLogDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing scriptLogDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("scriptLogDirectory may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankScriptLogDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory:\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing scriptLogDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("scriptLogDirectory may not be empty (was )"));
      }
    }

    @Test
    public void testMissingConfigDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile
            .getAbsolutePath());
        fail("Building deployer config object should fail with missing configDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("configDirectory may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankConfigDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: \"/tmp/scriptRunnerTest/vibs\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing configDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("configDirectory may not be empty (was )"));
      }
    }

    @Test
    public void testMissingVibDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with missing vibDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("vibDirectory may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankVibDirectory() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory:\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank vibDirectory field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("vibDirectory may not be empty (was )"));
      }
    }

    @Test
    public void testMissingTenantName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "projectName: project\n" +
          "resourceTicketName: \n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank tenantName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("tenantName may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankTenantName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: \n" +
          "projectName: project\n" +
          "resourceTicketName: \n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank tenantName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("tenantName may not be empty (was )"));
      }
    }

    @Test
    public void testMissingProjectName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "resourceTicketName: \n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank projectName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("projectName may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankProjectName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: \n" +
          "resourceTicketName: \n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank projectName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("projectName may not be empty (was )"));
      }
    }

    @Test
    public void testMissingResourceTicketName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank resourceTicketName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("resourceTicketName may not be empty (was null)"));
      }
    }

    @Test
    public void testBlankResourceTicketName() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "resourceTicketName: \n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail with blank resourceTicketName field");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("resourceTicketName may not be empty (was )"));
      }
    }

    @Test
    public void testMissingMaxMemoryGb() throws IOException {
      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "resourceTicketName: resourceTicketName\n" +
          "maxVmCount: 1\n";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        assertEquals(deployerContext.getMaxMemoryGb(), DeployerDefaults.DEFAULT_MAX_MEMORY_GB);
      } catch (Exception e) {
        fail(e.getMessage());
      }
    }

    @Test
    public void testMaxMemoryGbOutOfRange() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "resourceTicketName: resourceTicketName\n" +
          "maxMemoryGb: 0\n" +
          "maxVmCount: 1";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail when maxMemoryGb is < 1");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("maxMemoryGb must be between 1 and"));
      }
    }

    @Test
    public void testMissingMaxVmCount() throws IOException {
      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "resourceTicketName: resourceTicketName\n" +
          "maxMemoryGb: 10\n";
      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        deployerContext = ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        assertEquals(deployerContext.getMaxVmCount(), DeployerDefaults.DEFAULT_MAX_VM_COUNT);
      } catch (Exception e) {
        fail(e.getMessage());
      }
    }

    @Test
    public void testMaxVmCountOutOfRange() throws IOException {

      String configFileContents = "" +
          "enableSyslog: true\n" +
          "scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"\n" +
          "scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"\n" +
          "configDirectory: \"/tmp/deployAgent/configurations\"\n" +
          "syslogEndpoint: \"syslog endpoint\"\n" +
          "vibDirectory: vibdir\n" +
          "apifeEndpoint: endpoint\n" +
          "tenantName: tenant\n" +
          "projectName: project\n" +
          "resourceTicketName: resourceTicketName\n" +
          "maxMemoryGb: 10\n" +
          "maxVmCount: 0";

      File configFile = new File(storageDirectory, "config.yml");
      createConfigFile(configFile, configFileContents);

      try {
        ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
        fail("Building deployer config object should fail when maxVmCount is < 1");
      } catch (BadConfigException e) {
        assertThat(e.getMessage(), containsString("maxVmCount must be between 1 and"));
      }
    }

    @Test
    public void testIncorrectOverridesForDefaults() throws IOException {

      // Tuples representing "KEY", valid_value, invalid_value
      Object[][] testData = new Object[][]{
          {"corePoolSize", DeployerDefaults.CORE_POOL_SIZE, 0},
          { "xenonRetryCount", DeployerDefaults.DEFAULT_XENON_RETRY_COUNT, 0 },
          { "xenonRetryIntervalMs", DeployerDefaults.DEFAULT_XENON_RETRY_INTERVAL_MILLISECOND, 0 },
          {"deployerPort", DeployerDefaults.DEPLOYER_PORT_NUMBER, 0},
          {"keepAliveTime", DeployerDefaults.KEEP_ALIVE_TIME, 0},
          {"maxMemoryGb", DeployerDefaults.DEFAULT_MAX_MEMORY_GB, 0},
          {"maxVmCount", DeployerDefaults.DEFAULT_MAX_VM_COUNT, 0},
          {"maximumPoolSize", DeployerDefaults.MAXIMUM_POOL_SIZE, 0},
          {"pollingIntervalMs", DeployerDefaults.DEFAULT_POLLING_INTERVAL_MILLISECOND, 0},
          {"scriptTimeoutSec", DeployerDefaults.SCRIPT_TIMEOUT_IN_SECONDS, 0},
          {"taskPollDelay", DeployerDefaults.DEFAULT_TASK_POLL_DELAY, 0},
          {"waitForServiceMaxRetryCount", DeployerDefaults.DEFAULT_WAIT_FOR_SERVICE_MAX_RETRY_COUNT, 0},
      };

      for (int i = 0; i < testData.length; i++) {
        StringBuilder sb = new StringBuilder();

        // Construct list using valid default values
        for (int j = 0; j < i - 1; j++) {
          sb.append(testData[j][0]).append(": ").append(testData[j][1]).append("\n");
        }
        // Append invalid value for the ith config entry
        sb.append(testData[i][0]).append(": ").append(testData[i][2]).append("\n");
        sb.append("enableSyslog: true").append("\n");
        sb.append("scriptDirectory: \"/tmp/scriptRunnerTest/scripts\"").append("\n");
        sb.append("scriptLogDirectory: \"/tmp/scriptRunnerTest/logs\"").append("\n");
        sb.append("configDirectory: \"/tmp/deployAgent/configurations\"").append("\n");
        sb.append("syslogEndpoint: \"syslog endpoint\"").append("\n");
        sb.append("vibDirectory: vibdir").append("\n");
        sb.append("apifeEndpoint: endpoint").append("\n");
        sb.append("tenantName: tenant").append("\n");
        sb.append("projectName: project").append("\n");
        sb.append("resourceTicketName: resourceTicketName").append("\n");

        File configFile = new File(storageDirectory, "config.yml");
        createConfigFile(configFile, sb.toString());

        try {
          ConfigBuilder.build(DeployerContext.class, configFile.getAbsolutePath());
          fail(String.format(
              "Deployer config should fail for incorrect default override %s = %s",
              (String) testData[i][0],
              testData[i][2]));
        } catch (BadConfigException e) {
          assertThat(e.getMessage(), containsString((String) testData[i][0]));
        }
      }
    }
  }
}
