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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.Config;
import com.vmware.photon.controller.housekeeper.ConfigTest;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.LuceneQueryTaskFactoryService;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.inject.Injector;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link HousekeeperDcpServiceHost}.
 */
public class HousekeeperDcpServiceHostTest {

  private static File storageDir;
  private static final String configFilePath = "/config.yml";

  /**
   * Maximum time to wait for all factories to become available.
   */
  private static final long SERVICES_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

  private Injector injector;
  private HousekeeperDcpServiceHost host;

  private String[] serviceSelfLinks = new String[]{
      ImageReplicatorServiceFactory.SELF_LINK,
      ImageCopyServiceFactory.SELF_LINK,
      ImageRemoverServiceFactory.SELF_LINK,
      ImageDatastoreSweeperServiceFactory.SELF_LINK,
      ImageCleanerServiceFactory.SELF_LINK,
      ImageCleanerTriggerServiceFactory.SELF_LINK,
      ImageDeleteServiceFactory.SELF_LINK,
      ImageServiceFactory.SELF_LINK,
      TaskSchedulerServiceFactory.SELF_LINK,
      HousekeeperDcpServiceHost.getTriggerCleanerServiceUri(),
      HousekeeperDcpServiceHost.IMAGE_COPY_SCHEDULER_SERVICE,
      HousekeeperDcpServiceHost.IMAGE_DELETE_SCHEDULER_SERVICE
  };

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Exception {

      Config config = ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource(configFilePath).getPath());
      storageDir = new File(config.getDcp().getStoragePath());
      FileUtils.deleteDirectory(storageDir);

      injector = TestHelper.createInjector(configFilePath);
    }

    @AfterMethod
    public void tearDown() throws Exception {
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStoragePathExists() throws IOException {
      // make sure folder exists
      storageDir.mkdirs();

      HousekeeperDcpServiceHost host = injector.getInstance(HousekeeperDcpServiceHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testStoragePathDoesNotExist() throws Exception {
      // make sure folder does not exist
      FileUtils.deleteDirectory(storageDir);

      HousekeeperDcpServiceHost host = injector.getInstance(HousekeeperDcpServiceHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      HousekeeperDcpServiceHost host = injector.getInstance(HousekeeperDcpServiceHost.class);
      assertThat(host.getPort(), is(16001));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(16001));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }

    @Test
    public void testGetHostClientFactory() {
      HousekeeperDcpServiceHost host = injector.getInstance(HousekeeperDcpServiceHost.class);
      assertThat(host.getHostClient(), notNullValue());
    }

    @Test
    public void testGetZookeeperHostMonitor() {
      HousekeeperDcpServiceHost host = injector.getInstance(HousekeeperDcpServiceHost.class);
      assertThat(host.getZookeeperHostMonitor(), notNullValue());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetZookeeperHostMonitorNull() throws Throwable {
      CloudStoreHelper cloudStoreHelper = injector.getInstance(CloudStoreHelper.class);
      new HousekeeperDcpServiceHost(cloudStoreHelper, "localhost", 16001, storageDir.getPath(),
          injector.getInstance(HostClientFactory.class), null);
    }

  }

  /**
   * Tests for the start method.
   */
  public class StartTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      host = injector.getInstance(HousekeeperDcpServiceHost.class);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);
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
      assertThat(host.checkServiceAvailable(LuceneQueryTaskFactoryService.SELF_LINK), is(true));

      for (String selfLink : serviceSelfLinks) {
        assertThat(
            String.format("Failed to start service: %s", selfLink),
            host.checkServiceAvailable(selfLink),
            is(true));
      }
    }
  }

  /**
   * Tests for the isReady method.
   */
  public class IsReadyTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);

      host = injector.getInstance(HousekeeperDcpServiceHost.class);
      host.start();
      ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testAllReady() {
      assertThat(host.isReady(), is(true));
    }

    @Test(enabled = false)
    public void testNotReady() {
      doReturn(false).when(host).checkServiceAvailable(anyString());
      assertThat(host.isReady(), is(false));
    }
  }

  /**
   * Tests for joining node group.
   */
  public class JoinNodeGroupTest {

    private final long maintenanceInterval = TimeUnit.MILLISECONDS.toMicros(500);
    private final File storageDir2 = new File("/tmp/dcp/16002/");
    private HousekeeperDcpServiceHost host2;

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);

      host = new HousekeeperDcpServiceHost(
          injector.getInstance(CloudStoreHelper.class),
          "0.0.0.0", 16000, storageDir.getPath(),
          injector.getInstance(HostClientFactory.class),
          injector.getInstance(ZookeeperHostMonitor.class));
      host.setMaintenanceIntervalMicros(maintenanceInterval);
      host.start();
      ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());

      host2 = new HousekeeperDcpServiceHost(
          injector.getInstance(CloudStoreHelper.class),
          "0.0.0.0", 16002, storageDir2.getPath(),
          injector.getInstance(HostClientFactory.class),
          injector.getInstance(ZookeeperHostMonitor.class));
      host2.setMaintenanceIntervalMicros(maintenanceInterval);
      host2.start();
      ServiceHostUtils.waitForServiceAvailability(host2, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);

      if (host2 != null) {
        host2.stop();
      }
      FileUtils.deleteDirectory(storageDir2);
    }

    @Test
    public void testJoinNodeGroup() throws Throwable {
      ServiceHostUtils.joinNodeGroup(host2, host.getUri().getHost(), host.getPort());

      ServiceHostUtils.waitForNodeGroupConvergence(
          new HousekeeperDcpServiceHost[]{host, host2},
          ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_SLEEP);
    }
  }
}
