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

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.DeployerConfigTest;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements tests for the {@link DeployerXenonServiceHost} class.
 */
public class DeployerXenonServiceHostTest {

  private static File storageDir;

  private static final String configFilePath = "/config.yml";

  private DeployerXenonServiceHost host;
  private Collection<String> serviceSelfLinks;
  private DeployerConfig deployerConfig;
  private ServerSet cloudStoreServerSet;
  private AgentControlClientFactory agentControlClientFactory;
  private HostClientFactory hostClientFactory;
  private HttpFileServiceClientFactory httpFileServiceClientFactory;
  private ListeningExecutorService listeningExecutorService;
  private ApiClientFactory apiClientFactory;
  private DockerProvisionerFactory dockerProvisionerFactory;
  private AuthHelperFactory authHelperFactory;
  private HealthCheckHelperFactory healthCheckHelperFactory;
  private ServiceConfiguratorFactory serviceConfiguratorFactory;
  private ZookeeperClientFactory zookeeperClientFactory;
  private HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
  private ClusterManagerFactory clusterManagerFactory;
  private NsxClientFactory nsxClientFactory;

  private void waitForServicesStartup(DeployerXenonServiceHost host)
      throws TimeoutException, InterruptedException, NoSuchFieldException, IllegalAccessException {

    serviceSelfLinks = ServiceHostUtils.getServiceSelfLinks(
        DeployerXenonServiceHost.FACTORY_SERVICE_FIELD_NAME_SELF_LINK,
        DeployerXenonServiceHost.FACTORY_SERVICES);
    serviceSelfLinks.add(DeployerXenonServiceHost.UPLOAD_VIB_WORK_QUEUE_SELF_LINK);

    final CountDownLatch latch = new CountDownLatch(serviceSelfLinks.size());
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        latch.countDown();
      }
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
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          DeployerConfigTest.class.getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreServerSet = mock(ServerSet.class);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      zookeeperClientFactory = mock(ZookeeperClientFactory.class);
      hostManagementVmAddressValidatorFactory = mock(HostManagementVmAddressValidatorFactory.class);
      clusterManagerFactory = mock(ClusterManagerFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);

      storageDir = new File(deployerConfig.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);

    }

    @BeforeMethod
    public void setUp() throws Throwable {
      host = new DeployerXenonServiceHost(
          deployerConfig.getXenonConfig(),
          cloudStoreServerSet,
          deployerConfig.getDeployerContext(),
          deployerConfig.getContainersConfig(),
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);
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

      // Check that host creates storage directory
      host = new DeployerXenonServiceHost(
          deployerConfig.getXenonConfig(),
          cloudStoreServerSet,
          deployerConfig.getDeployerContext(),
          deployerConfig.getContainersConfig(),
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);

      assertThat(storageDir.exists(), is(true));
      assertThat(host, is(notNullValue()));
    }

    @Test
    public void testParams() {
      assertThat(host.getPort(), is(18001));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(18001));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
    }
  }

  /**
   * Tests for the start method.
   */
  public class StartTest {

    @BeforeClass
    private void setUpClass() throws IOException, BadConfigException {
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          DeployerConfigTest.class.getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreServerSet = mock(ServerSet.class);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      zookeeperClientFactory = mock(ZookeeperClientFactory.class);
      hostManagementVmAddressValidatorFactory = mock(HostManagementVmAddressValidatorFactory.class);
      clusterManagerFactory = mock(ClusterManagerFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);

      storageDir = new File(deployerConfig.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      host = new DeployerXenonServiceHost(
          deployerConfig.getXenonConfig(),
          cloudStoreServerSet,
          deployerConfig.getDeployerContext(),
          deployerConfig.getContainersConfig(),
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);
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
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          DeployerConfigTest.class.getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreServerSet = mock(ServerSet.class);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      zookeeperClientFactory = mock(ZookeeperClientFactory.class);
      hostManagementVmAddressValidatorFactory = mock(HostManagementVmAddressValidatorFactory.class);
      clusterManagerFactory = mock(ClusterManagerFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);

      storageDir = new File(deployerConfig.getXenonConfig().getStoragePath());
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      host = new DeployerXenonServiceHost(
          deployerConfig.getXenonConfig(),
          cloudStoreServerSet,
          deployerConfig.getDeployerContext(),
          deployerConfig.getContainersConfig(),
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);
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
      startHost();
      assertThat(host.isReady(), is(true));
    }

    @Test
    public void testNotReady() throws Throwable {
      assertThat(host.isReady(), is(false));
    }

    private void startHost() throws Throwable {
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
    private DeployerXenonServiceHost host2;

    @BeforeClass
    private void setUpClass() throws IOException {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreServerSet = mock(ServerSet.class);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      zookeeperClientFactory = mock(ZookeeperClientFactory.class);
      hostManagementVmAddressValidatorFactory = mock(HostManagementVmAddressValidatorFactory.class);
      clusterManagerFactory = mock(ClusterManagerFactory.class);
      nsxClientFactory = mock(NsxClientFactory.class);

      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    private void setUp() throws Throwable {
      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress("0.0.0.0");
      xenonConfig.setPort(18001);
      xenonConfig.setStoragePath(storageDir.getAbsolutePath());

      host = new DeployerXenonServiceHost(
          xenonConfig,
          cloudStoreServerSet,
          null,
          null /* containersConfig */,
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);

      host.setMaintenanceIntervalMicros(maintenanceInterval);
      host.start();
      waitForServicesStartup(host);

      XenonConfig xenonConfig2 = new XenonConfig();
      xenonConfig2.setBindAddress("0.0.0.0");
      xenonConfig2.setPort(18002);
      xenonConfig2.setStoragePath(storageDir2.getAbsolutePath());

      host2 = new DeployerXenonServiceHost(
          xenonConfig2,
          cloudStoreServerSet,
          null,
          null /* containersConfig */,
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperClientFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);

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
          new DeployerXenonServiceHost[]{host, host2},
          ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          MultiHostEnvironment.TEST_NODE_GROUP_CONVERGENCE_SLEEP);
    }
  }
}
