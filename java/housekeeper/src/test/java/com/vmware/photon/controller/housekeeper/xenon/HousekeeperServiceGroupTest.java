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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.apibackend.ApiBackendFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.scheduler.TaskTriggerFactoryService;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.housekeeper.ConfigTest;
import com.vmware.photon.controller.housekeeper.HousekeeperConfig;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;
import com.vmware.xenon.services.common.ServiceUriPaths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link HousekeeperServiceGroup}.
 */
public class HousekeeperServiceGroupTest {

  private static File storageDir;
  private static final String configFilePath = "/config.yml";

  /**
   * Maximum time to wait for all factories to become available.
   */
  private static final long SERVICES_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

  private PhotonControllerXenonHost host;
  private HousekeeperServiceGroup housekeeperServiceGroup;
  private HousekeeperConfig config;
  private CloudStoreHelper cloudStoreHelper;
  private HostClientFactory hostClientFactory;
  private ServiceConfigFactory serviceConfigFactory;
  private NsxClientFactory nsxClientFactory;
  private ServerSet cloudStoreServerSet;

  private String[] serviceSelfLinks = createServiceSelfLinks();

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
    public void setUpClass() throws Exception {
      config = ConfigBuilder.build(HousekeeperConfig.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      cloudStoreServerSet = mock(ServerSet.class);
      cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      hostClientFactory = mock(HostClientFactory.class);
      HostClient client = mock(HostClient.class);
      when(hostClientFactory.create()).thenReturn(client);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      ServiceConfig serviceConfig = mock(ServiceConfig.class);
      when(serviceConfigFactory.create(anyString())).thenReturn(serviceConfig);
      nsxClientFactory = new NsxClientFactory();

      storageDir = new File(config.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      host = new PhotonControllerXenonHost(config.getXenonConfig(), hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      housekeeperServiceGroup = new HousekeeperServiceGroup();
      host.registerHousekeeper(housekeeperServiceGroup);
    }

    @AfterMethod
    public void tearDown() throws Exception {
      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStoragePathExists() throws IOException {
      // make sure folder exists
      storageDir.mkdirs();

      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testStoragePathDoesNotExist() throws Throwable {
      // make sure folder does not exist
      FileUtils.deleteDirectory(storageDir);
      assertThat(storageDir.exists(), is(false));

      // Check that the host will create the storage directory.
      host = new PhotonControllerXenonHost(config.getXenonConfig(), hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      housekeeperServiceGroup = new HousekeeperServiceGroup();
      host.registerHousekeeper(housekeeperServiceGroup);

      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      assertThat(host.getPort(), is(16000));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(16000));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }

    @Test
    public void testGetHostClientFactory() {
      assertThat(host.getHostClient(), notNullValue());
    }

    @Test
    public void testGetServiceConfig() {
      assertThat(host.getServiceConfig(), notNullValue());
    }

    @Test
    public void testGetNsxClientFactory() {
      assertThat(host.getNsxClientFactory(), notNullValue());
    }
  }

  /**
   * Tests for the start method.
   */
  public class StartTest {

    @BeforeClass
    private void setUpClass() throws Throwable {
      config = ConfigBuilder.build(HousekeeperConfig.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      cloudStoreServerSet = mock(ServerSet.class);
      cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      nsxClientFactory = new NsxClientFactory();

      storageDir = new File(config.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      host = new PhotonControllerXenonHost(config.getXenonConfig(), hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      housekeeperServiceGroup = new HousekeeperServiceGroup();
      host.registerHousekeeper(housekeeperServiceGroup);
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
      assertThat(host.checkServiceAvailable(ServiceUriPaths.CORE_QUERY_TASKS), is(true));

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

    @BeforeClass
    private void setUpClass() throws Throwable {
      config = ConfigBuilder.build(HousekeeperConfig.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      cloudStoreServerSet = mock(ServerSet.class);
      cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      nsxClientFactory = new NsxClientFactory();

      storageDir = new File(config.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      host = new PhotonControllerXenonHost(config.getXenonConfig(), hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      housekeeperServiceGroup = new HousekeeperServiceGroup();
      host.registerHousekeeper(housekeeperServiceGroup);
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
    private final File storageDir2 = new File("/tmp/xenon/16001/");
    private PhotonControllerXenonHost host2;

    @BeforeClass
    private void setUpClass() throws Throwable {
      config = ConfigBuilder.build(HousekeeperConfig.class,
          ConfigTest.class.getResource(configFilePath).getPath());

      cloudStoreServerSet = mock(ServerSet.class);
      cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
      hostClientFactory = mock(HostClientFactory.class);
      serviceConfigFactory = mock(ServiceConfigFactory.class);
      nsxClientFactory = new NsxClientFactory();

      storageDir = new File(config.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress("0.0.0.0");
      xenonConfig.setPort(16000);
      xenonConfig.setStoragePath(storageDir.getAbsolutePath());

      host = new PhotonControllerXenonHost(xenonConfig, hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      housekeeperServiceGroup = new HousekeeperServiceGroup();
      host.registerHousekeeper(housekeeperServiceGroup);

      host.setMaintenanceIntervalMicros(maintenanceInterval);
      host.start();
      ServiceHostUtils.waitForServiceAvailability(host, SERVICES_STARTUP_TIMEOUT, serviceSelfLinks.clone());

      XenonConfig xenonConfig2 = new XenonConfig();
      xenonConfig2.setBindAddress("0.0.0.0");
      xenonConfig2.setPort(16001);
      xenonConfig2.setStoragePath(storageDir2.getAbsolutePath());

      host2 = new PhotonControllerXenonHost(xenonConfig2, hostClientFactory, null,
          serviceConfigFactory, nsxClientFactory, cloudStoreHelper);
      HousekeeperServiceGroup housekeeperServiceGroup2 = new HousekeeperServiceGroup();
      host2.registerHousekeeper(housekeeperServiceGroup2);
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
          new PhotonControllerXenonHost[]{host, host2},
          ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_SLEEP);
    }
  }

  private String[] createServiceSelfLinks() {
    List<String> apiBackendServiceSelfLinks = new ArrayList<>();
    Set<Class<? extends Service>> set = ApiBackendFactory.FACTORY_SERVICES_MAP.keySet();
    for (Class cls : set) {
      try {
        Field fld = cls.getField("FACTORY_LINK");
        apiBackendServiceSelfLinks.add((String) fld.get(null));
      } catch (IllegalAccessException | NoSuchFieldException e) {
        // Simply ignore them
      }
    }

    return ArrayUtils.addAll(apiBackendServiceSelfLinks.toArray(new String[0]),
        RootNamespaceService.SELF_LINK,
        ImageReplicatorServiceFactory.SELF_LINK,
        ImageCopyServiceFactory.SELF_LINK,
        ImageHostToHostCopyServiceFactory.SELF_LINK,
        ImageDatastoreSweeperServiceFactory.SELF_LINK,
        ImageCleanerServiceFactory.SELF_LINK,
        ImageCleanerTriggerServiceFactory.SELF_LINK,
        ImageSeederSyncServiceFactory.SELF_LINK,
        TaskSchedulerServiceFactory.SELF_LINK,
        TaskTriggerFactoryService.SELF_LINK,
        HousekeeperServiceGroup.getTriggerCleanerServiceUri(),
        HousekeeperServiceGroup.getImageSeederSyncTriggerServiceUri(),
        HousekeeperServiceGroup.IMAGE_COPY_SCHEDULER_SERVICE);
  }
}
