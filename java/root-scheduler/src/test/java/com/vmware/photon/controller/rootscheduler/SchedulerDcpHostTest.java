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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.rootscheduler.helpers.TestHelper;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements tests for the {@link SchedulerDcpHost} class.
 */
public class SchedulerDcpHostTest {

  private static File storageDir;

  private static final String configFilePath = "/config.yml";

  private Injector injector;
  private SchedulerDcpHost host;
  private Collection<String> serviceSelfLinks;

  private void waitForServicesStartup(SchedulerDcpHost host)
      throws TimeoutException, InterruptedException, NoSuchFieldException, IllegalAccessException {

    serviceSelfLinks = ServiceHostUtils.getServiceSelfLinks(
        SchedulerDcpHost.FACTORY_SERVICE_FIELD_NAME_SELF_LINK,
        SchedulerDcpHost.FACTORY_SERVICES);

    final CountDownLatch latch = new CountDownLatch(serviceSelfLinks.size());
    Operation.CompletionHandler handler = (operation, throwable) -> {
      latch.countDown();
    };

    String[] links = new String[serviceSelfLinks.size()];
    host.registerForServiceAvailability(handler, serviceSelfLinks.toArray(links));
    if (!latch.await(10, TimeUnit.SECONDS)) {
      throw new TimeoutException();
    }
  }

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

    @BeforeClass
    public void setUpClass() throws IOException, BadConfigException {
      Config config = ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      storageDir = new File(config.getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUp() throws Exception {
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

      SchedulerDcpHost host = injector.getInstance(SchedulerDcpHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testStoragePathDoesNotExist() throws Exception {
      // make sure folder does not exist
      FileUtils.deleteDirectory(storageDir);

      SchedulerDcpHost host = injector.getInstance(SchedulerDcpHost.class);
      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      SchedulerDcpHost host = injector.getInstance(SchedulerDcpHost.class);
      assertThat(host.getPort(), is(15001));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(15001));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }
  }

  /**
   * Tests for the start method.
   */
  public class StartTest {

    @BeforeClass
    private void setUpClass() throws IOException, BadConfigException {
      Config config = ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      storageDir = new File(config.getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      host = injector.getInstance(SchedulerDcpHost.class);
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
        waitForServicesStartup(host);
      } catch (TimeoutException e) {
        // N.B. This exception is swallowed so that the asserts below will
        //      identify the service which failed to start.
      }

      assertThat(host.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP), is(true));
      assertThat(host.checkServiceAvailable(LuceneDocumentIndexService.SELF_LINK), is(true));
      assertThat(host.checkServiceAvailable(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS), is(true));

      for (String serviceLink : serviceSelfLinks) {
        assertThat(
            String.format("Failed to start service: %s", serviceLink),
            host.checkServiceAvailable(serviceLink),
            is(true));
      }
    }
  }

  /**
   * Tests for the isReady method.
   */
  public class IsReadyTest {

    @BeforeClass
    private void setUpClass() throws IOException, BadConfigException {
      Config config = ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      storageDir = new File(config.getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);

      host = injector.getInstance(SchedulerDcpHost.class);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (host != null) {
        host.stop();
      }
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testAllReady() throws Throwable {
      startHost(host);
      assertThat(host.isReady(), is(true));
    }

    @Test
    public void testNotReady() throws Throwable {
      // We make a spy of the host so we can mock it. We used to make the spy in
      // TestRootSchedulerModule,
      // but the spy hosts breaks for tests that run long enough to invoke the
      // host maintenance. (Making
      // the spy makes an extra object, and the original isn't initialized
      // properly)
      SchedulerDcpHost spyHost = spy(host);
      doReturn(false).when(spyHost).checkServiceAvailable(anyString());
      assertThat(spyHost.isReady(), is(false));
    }

    private void startHost(SchedulerDcpHost host) throws Throwable {
      host.start();
      waitForServicesStartup(host);
    }
  }

  /**
   * Tests for joining node group.
   */
  public class JoinNodeGroupTest {

    private final File storageDir2 = new File("/tmp/dcp/18002/");
    private final long maintenanceInterval = TimeUnit.MILLISECONDS.toMicros(500);
    private SchedulerDcpHost host2;

    @BeforeClass
    private void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);

      host = new SchedulerDcpHost(
          "0.0.0.0", 18000, storageDir.getPath());

      host.setMaintenanceIntervalMicros(maintenanceInterval);
      host.start();
      waitForServicesStartup(host);

      host2 = new SchedulerDcpHost(
          "0.0.0.0", 18002, storageDir2.getPath());

      host2.setMaintenanceIntervalMicros(maintenanceInterval);
      host2.start();
      waitForServicesStartup(host2);
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
          new SchedulerDcpHost[]{host, host2},
          ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          MultiHostEnvironment.TEST_NODE_GROUP_CONVERGENCE_SLEEP);
    }
  }
}
