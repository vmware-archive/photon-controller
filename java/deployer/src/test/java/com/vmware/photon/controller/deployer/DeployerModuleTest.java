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

package com.vmware.photon.controller.deployer;

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link DeployerModule} class.
 */
public class DeployerModuleTest {

  /**
   * This dummy test case enables Intellij to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * Test the Guice injection functionality.
   */
  protected class InjectorTest {

    private Injector injector;
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

    @BeforeMethod
    public void setUp() throws BadConfigException {
      injector = TestHelper.createInjector("/config.yml");
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          DeployerConfigTest.class.getResource("/config.yml").getPath());
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
    }

    @Test
    public void testConfig() {
      TestHelper.TestInjectedConfig test = injector.getInstance(TestHelper.TestInjectedConfig.class);
      assertThat(test.getBind(), is("localhost"));
      assertThat(test.getRegistrationAddress(), is("localhost"));
      assertThat(test.getPort(), is(18001));
      assertThat(test.getPath(), is("/tmp/dcp/deployer/"));
      String[] peerNodes = {"http://localhost:18001"};
      assertThat(test.getPeerNodes(), is(peerNodes));
    }

    @Test
    public void testZookeeperClientInjection() throws Throwable {
      DeployerXenonServiceHost host = new DeployerXenonServiceHost(
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
      ZookeeperClient zookeeperClient =
          ((ZookeeperClientFactoryProvider) host).getZookeeperServerSetFactoryBuilder().create();
    }
  }
}
