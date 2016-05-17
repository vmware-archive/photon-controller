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

package com.vmware.photon.controller.dhcpagent.xenon;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfig;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfigTest;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestHelper;
import com.vmware.photon.controller.dhcpagent.xenon.service.StatusService;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.inject.Injector;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements tests for the {@link DHCPAgentXenonHost} class.
 */
public class DHCPAgentXenonHostTest {

  private static File storageDir;

  private static final String configFilePath = "/config.yml";

  /**
   * Maximum time to wait for all factories to become available.
   */
  private static final long SERVICES_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
  private Injector injector;
  private DHCPAgentXenonHost host;
  private String[] serviceSelfLinks = new String[]{
      StatusService.SELF_LINK,

      // discovery
      RootNamespaceService.SELF_LINK,
  };

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeClass
    public void setUpClass() throws IOException, BadConfigException {
      DHCPAgentConfig config = ConfigBuilder.build(DHCPAgentConfig.class,
              DHCPAgentConfigTest.class.getResource(configFilePath).getPath());
      storageDir = new File(config.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUp() throws Exception {
      injector = TestHelper.createInjector(configFilePath, new DnsmasqDriver("/usr/local/bin/dhcp_release",
              DHCPAgentXenonHostTest.class.getResource("/scripts/release-ip.sh").getPath(),
              DHCPAgentXenonHostTest.class.getResource("/scripts/dhcp-status.sh").getPath()));
    }

    @AfterMethod
    public void tearDown() throws Exception {
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStoragePathExists() throws IOException {
      // make sure folder exists
      storageDir.mkdirs();

      DHCPAgentXenonHost host = injector.getInstance(DHCPAgentXenonHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testStoragePathDoesNotExist() throws Exception {
      // make sure folder does not exist
      FileUtils.deleteDirectory(storageDir);

      DHCPAgentXenonHost host = injector.getInstance(DHCPAgentXenonHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      DHCPAgentXenonHost host = injector.getInstance(DHCPAgentXenonHost.class);
      assertThat(host.getPort(), is(17000));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(17000));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }
  }

  /**
   * Tests for the start method.
   */
  public class StartTest {
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath, new DnsmasqDriver("/usr/local/bin/dhcp_release",
              DHCPAgentXenonHostTest.class.getResource("/scripts/release-ip.sh").getPath(),
              DHCPAgentXenonHostTest.class.getResource("/scripts/dhcp-status.sh").getPath()));
      host = injector.getInstance(DHCPAgentXenonHost.class);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
    }

    @Test
    public void testStart() throws Throwable {
      host.start();

      try {
        ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
      } catch (TimeoutException e) {
        // we swallow up this exception so that down below we get a better message of what
        // service failed to start.
      }

      assertThat(host.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP), is(true));
      assertThat(host.checkServiceAvailable(LuceneDocumentIndexService.SELF_LINK), is(true));
      assertThat(host.checkServiceAvailable(ServiceUriPaths.CORE_QUERY_TASKS), is(true));

      for (String selfLink : serviceSelfLinks) {
        assertThat(
            String.format("Failed to start service: %s", selfLink),
            host.checkServiceAvailable(selfLink),
            is(true));
      }

      assertThat(host.getClient().getConnectionLimitPerHost(),
          is(DHCPAgentXenonHost.DEFAULT_CONNECTION_LIMIT_PER_HOST));
    }
  }

  /**
   * Tests for the isReady method.
   */
  public class IsReadyTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath, new DnsmasqDriver("/usr/local/bin/dhcp_release",
              DHCPAgentXenonHostTest.class.getResource("/scripts/release-ip.sh").getPath(),
              DHCPAgentXenonHostTest.class.getResource("/scripts/dhcp-status.sh").getPath()));
      host = injector.getInstance(DHCPAgentXenonHost.class);
      host.start();
      ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
    }

    @Test
    public void testAllReady() {
      assertThat(host.isReady(), is(true));
    }
  }
}
