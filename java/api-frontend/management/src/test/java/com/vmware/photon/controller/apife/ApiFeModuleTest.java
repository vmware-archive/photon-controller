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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.apife.auth.fetcher.Cluster;
import com.vmware.photon.controller.apife.auth.fetcher.ClusterSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Deployment;
import com.vmware.photon.controller.apife.auth.fetcher.Disk;
import com.vmware.photon.controller.apife.auth.fetcher.DiskSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Multiplexed;
import com.vmware.photon.controller.apife.auth.fetcher.MultiplexedSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.None;
import com.vmware.photon.controller.apife.auth.fetcher.NoneSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Project;
import com.vmware.photon.controller.apife.auth.fetcher.ProjectSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.ResourceTicket;
import com.vmware.photon.controller.apife.auth.fetcher.ResourceTicketSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.SecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Tenant;
import com.vmware.photon.controller.apife.auth.fetcher.TenantSecurityGroupFetcher;
import com.vmware.photon.controller.apife.auth.fetcher.Vm;
import com.vmware.photon.controller.apife.auth.fetcher.VmSecurityGroupFetcher;
import com.vmware.photon.controller.apife.backends.AttachedDiskBackend;
import com.vmware.photon.controller.apife.backends.AttachedDiskDcpBackend;
import com.vmware.photon.controller.apife.backends.DatastoreBackend;
import com.vmware.photon.controller.apife.backends.DatastoreDcpBackend;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.DeploymentDcpBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.DiskDcpBackend;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.EntityLockDcpBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.FlavorDcpBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.HostDcpBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.ImageDcpBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.NetworkDcpBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TaskDcpBackend;
import com.vmware.photon.controller.apife.backends.TombstoneBackend;
import com.vmware.photon.controller.apife.backends.TombstoneDcpBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.VmDcpBackend;
import com.vmware.photon.controller.apife.config.ApiFeConfiguration;
import com.vmware.photon.controller.apife.config.ApiFeConfigurationTest;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.config.ConfigurationUtils;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.servlet.RequestScoped;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests {@link ApiFeModule}.
 */
public class ApiFeModuleTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Helper class used to test ImageConfig injection.
   */
  public static class TestImageConfigInjection {
    public ImageConfig imageConfig;

    @Inject
    public TestImageConfigInjection(ImageConfig config) {
      this.imageConfig = config;
    }
  }

  /**
   * Helper class used to test ImageConfig injection.
   */
  public static class TestAuthConfigInjection {
    public AuthConfig config;

    @Inject
    public TestAuthConfigInjection(AuthConfig config) {
      this.config = config;
    }
  }

  /**
   * Helper class used to test PaginationConfig injection.
   */
  public static class TestPaginationConfigInjection {
    public PaginationConfig config;

    @Inject
    public TestPaginationConfigInjection(PaginationConfig config) {
      this.config = config;
    }
  }

  /**
   * Helper class used to test 'useVirtualNetwork' flag injection.
   */
  public static class TestUseVirtualNetworkFlag {
    public Boolean useVirtualNetwork;

    @Inject
    public TestUseVirtualNetworkFlag(@Named("useVirtualNetwork") Boolean useVirtualNetwork) {
      this.useVirtualNetwork = useVirtualNetwork;
    }
  }

  /**
   * Helper class used to test backend injection.
   */
  public static class DcpBackendDummyClient {
    public FlavorBackend flavorBackend;
    public ImageBackend imageBackend;
    public TaskBackend taskBackend;
    public NetworkBackend networkBackend;
    public DatastoreBackend datastoreBackend;
    public StepBackend stepBackend;
    public EntityLockBackend entityLockBackend;
    public ResourceTicketBackend resourceTicketBackend;
    public DiskBackend diskBackend;
    public AttachedDiskBackend attachedDiskBackend;
    public VmBackend vmBackend;
    public HostBackend hostBackend;
    public DeploymentBackend deploymentBackend;
    public TombstoneBackend tombstoneBackend;

    @Inject
    public DcpBackendDummyClient(FlavorBackend flavorBackend,
                                 ImageBackend imageBackend,
                                 TaskBackend taskBackend,
                                 NetworkBackend networkBackend,
                                 DatastoreBackend datastoreBackend,
                                 StepBackend stepBackend,
                                 EntityLockBackend entityLockBackend,
                                 ResourceTicketBackend resourceTicketBackend,
                                 DiskBackend diskBackend,
                                 AttachedDiskBackend attachedDiskBackend,
                                 VmBackend vmBackend,
                                 HostBackend hostBackend,
                                 DeploymentBackend deploymentBackend,
                                 TombstoneBackend tombstoneBackend) {
      this.flavorBackend = flavorBackend;
      this.imageBackend = imageBackend;
      this.taskBackend = taskBackend;
      this.networkBackend = networkBackend;
      this.datastoreBackend = datastoreBackend;
      this.stepBackend = stepBackend;
      this.entityLockBackend = entityLockBackend;
      this.resourceTicketBackend = resourceTicketBackend;
      this.diskBackend = diskBackend;
      this.attachedDiskBackend = attachedDiskBackend;
      this.vmBackend = vmBackend;
      this.hostBackend = hostBackend;
      this.deploymentBackend = deploymentBackend;
      this.tombstoneBackend = tombstoneBackend;
    }
  }

  /**
   * Helper class used to test security group fetcher injection.
   */
  public static class TestSecurityGroupFetcherInjection {
    public SecurityGroupFetcher noneFetcher;
    public SecurityGroupFetcher multiplexedFetcher;
    public SecurityGroupFetcher deploymentFetcher;
    public SecurityGroupFetcher tenantFetcher;
    public SecurityGroupFetcher projectFetcher;
    public SecurityGroupFetcher resourceTicketFetcher;
    public SecurityGroupFetcher clusterFetcher;
    public SecurityGroupFetcher diskFetcher;
    public SecurityGroupFetcher vmFetcher;

    @Inject
    public TestSecurityGroupFetcherInjection(
        @None SecurityGroupFetcher noneFetcher,
        @Multiplexed SecurityGroupFetcher multiplexedFetcher,
        @Deployment SecurityGroupFetcher deploymentFetcher,
        @Tenant SecurityGroupFetcher tenantFetcher,
        @Project SecurityGroupFetcher projectFetcher,
        @ResourceTicket SecurityGroupFetcher resourceTicketFetcher,
        @Cluster SecurityGroupFetcher clusterFetcher,
        @Disk SecurityGroupFetcher diskFetcher,
        @Vm SecurityGroupFetcher vmFetcher) {
      this.noneFetcher = noneFetcher;
      this.multiplexedFetcher = multiplexedFetcher;
      this.deploymentFetcher = deploymentFetcher;
      this.tenantFetcher = tenantFetcher;
      this.projectFetcher = projectFetcher;
      this.resourceTicketFetcher = resourceTicketFetcher;
      this.clusterFetcher = clusterFetcher;
      this.diskFetcher = diskFetcher;
      this.vmFetcher = vmFetcher;
    }
  }

  /**
   * Tests ImageConfig injection.
   */
  public class TestImageConfig {

    private Injector injector;

    @BeforeTest
    public void setUp() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      apiFeModule.setConfiguration(
          ConfigurationUtils.parseConfiguration(
              ApiFeConfigurationTest.class.getResource("/config_valid_image_replication_timeout.yml").getPath()
          )
      );

      injector = Guice.createInjector(
          apiFeModule,
          new ZookeeperModule(),
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          });
    }

    /**
     * Test that ImageConfig can be injected successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testImageConfigIsInjected() throws Throwable {
      TestImageConfigInjection configWrapper = injector.getInstance(TestImageConfigInjection.class);
      assertThat(configWrapper.imageConfig, notNullValue());
      assertThat(configWrapper.imageConfig.getReplicationTimeout().toSeconds(), is(600L));
    }
  }

  /**
   * Tests AuthConfig injection.
   */
  public class TestAuthConfig {

    private Injector injector;

    @BeforeTest
    public void setUp() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      apiFeModule.setConfiguration(
          ConfigurationUtils.parseConfiguration(
              ApiFeConfigurationTest.class.getResource("/config.yml").getPath()
          )
      );

      injector = Guice.createInjector(
          apiFeModule,
          new ZookeeperModule(),
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          });
    }

    /**
     * Test that AuthConfig can be injected successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testConfigIsInjected() throws Throwable {
      TestAuthConfigInjection configWrapper = injector.getInstance(TestAuthConfigInjection.class);
      assertThat(configWrapper.config, notNullValue());
      assertThat(configWrapper.config, instanceOf(AuthConfig.class));
      assertThat(configWrapper.config.isAuthEnabled(), is(true));
    }
  }

  /**
   * Tests backend injection.
   */
  public class TestBackendInjection {
    /**
     * Test that DCP backends can be injected successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testDcpBackendInjection() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      ApiFeConfiguration apiFeConfiguration = ConfigurationUtils.parseConfiguration(
          ApiFeConfigurationTest.class.getResource("/config.yml").getPath()
      );

      apiFeModule.setConfiguration(apiFeConfiguration);

      ZookeeperModule zookeeperModule = new ZookeeperModule();
      zookeeperModule.setConfig(apiFeConfiguration.getZookeeper());

      Injector injector = Guice.createInjector(
          apiFeModule,
          zookeeperModule,
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          });

      DcpBackendDummyClient dcpBackendDummyClient = injector.getInstance(DcpBackendDummyClient.class);
      assertThat(dcpBackendDummyClient.flavorBackend, notNullValue());
      assertThat(dcpBackendDummyClient.flavorBackend, instanceOf(FlavorDcpBackend.class));
      assertThat(dcpBackendDummyClient.imageBackend, notNullValue());
      assertThat(dcpBackendDummyClient.imageBackend, instanceOf(ImageDcpBackend.class));
      assertThat(dcpBackendDummyClient.networkBackend, notNullValue());
      assertThat(dcpBackendDummyClient.networkBackend, instanceOf(NetworkDcpBackend.class));
      assertThat(dcpBackendDummyClient.datastoreBackend, notNullValue());
      assertThat(dcpBackendDummyClient.datastoreBackend, instanceOf(DatastoreDcpBackend.class));
      assertThat(dcpBackendDummyClient.entityLockBackend, notNullValue());
      assertThat(dcpBackendDummyClient.entityLockBackend, instanceOf(EntityLockDcpBackend.class));
      assertThat(dcpBackendDummyClient.taskBackend, notNullValue());
      assertThat(dcpBackendDummyClient.taskBackend, instanceOf(TaskDcpBackend.class));
      assertThat(dcpBackendDummyClient.stepBackend, notNullValue());
      assertThat(dcpBackendDummyClient.stepBackend, instanceOf(TaskDcpBackend.class));
      assertThat(dcpBackendDummyClient.resourceTicketBackend, notNullValue());
      assertThat(dcpBackendDummyClient.resourceTicketBackend, instanceOf(ResourceTicketDcpBackend.class));
      assertThat(dcpBackendDummyClient.diskBackend, notNullValue());
      assertThat(dcpBackendDummyClient.diskBackend, instanceOf(DiskDcpBackend.class));
      assertThat(dcpBackendDummyClient.attachedDiskBackend, notNullValue());
      assertThat(dcpBackendDummyClient.attachedDiskBackend, instanceOf(AttachedDiskDcpBackend.class));
      assertThat(dcpBackendDummyClient.vmBackend, notNullValue());
      assertThat(dcpBackendDummyClient.vmBackend, instanceOf(VmDcpBackend.class));
      assertThat(dcpBackendDummyClient.tombstoneBackend, notNullValue());
      assertThat(dcpBackendDummyClient.tombstoneBackend, instanceOf(TombstoneDcpBackend.class));
      assertThat(dcpBackendDummyClient.hostBackend, notNullValue());
      assertThat(dcpBackendDummyClient.hostBackend, instanceOf(HostDcpBackend.class));
      assertThat(dcpBackendDummyClient.deploymentBackend, notNullValue());
      assertThat(dcpBackendDummyClient.deploymentBackend, instanceOf(DeploymentDcpBackend.class));
    }
  }

  /**
   * Tests backend injection.
   */
  public class TestSecurityGroupFetcher {
    /**
     * Test that fetchers can be injected successfully.
     *
     * @throws Throwable
     */
    @Test
    public void testInjection() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      ApiFeConfiguration apiFeConfiguration = ConfigurationUtils.parseConfiguration(
          ApiFeConfigurationTest.class.getResource("/config_min.yml").getPath()
      );

      apiFeModule.setConfiguration(apiFeConfiguration);

      ZookeeperModule zookeeperModule = new ZookeeperModule();
      zookeeperModule.setConfig(apiFeConfiguration.getZookeeper());

      Injector injector = Guice.createInjector(
          apiFeModule,
          zookeeperModule,
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          });

      TestSecurityGroupFetcherInjection subject = injector.getInstance(TestSecurityGroupFetcherInjection.class);
      assertThat(subject.noneFetcher, notNullValue());
      assertThat(subject.noneFetcher, instanceOf(NoneSecurityGroupFetcher.class));
      assertThat(subject.multiplexedFetcher, notNullValue());
      assertThat(subject.multiplexedFetcher, instanceOf(MultiplexedSecurityGroupFetcher.class));
      assertThat(subject.clusterFetcher, notNullValue());
      assertThat(subject.clusterFetcher, instanceOf(ClusterSecurityGroupFetcher.class));
      assertThat(subject.diskFetcher, notNullValue());
      assertThat(subject.diskFetcher, instanceOf(DiskSecurityGroupFetcher.class));
      assertThat(subject.projectFetcher, notNullValue());
      assertThat(subject.projectFetcher, instanceOf(ProjectSecurityGroupFetcher.class));
      assertThat(subject.resourceTicketFetcher, notNullValue());
      assertThat(subject.resourceTicketFetcher, instanceOf(ResourceTicketSecurityGroupFetcher.class));
      assertThat(subject.tenantFetcher, notNullValue());
      assertThat(subject.tenantFetcher, instanceOf(TenantSecurityGroupFetcher.class));
      assertThat(subject.vmFetcher, notNullValue());
      assertThat(subject.vmFetcher, instanceOf(VmSecurityGroupFetcher.class));
    }
  }

  /**
   * Tests for PaginationConfig injection.
   */
  public class TestPaginationConfig {

    @Test
    public void testPaginationConfigInjected() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      apiFeModule.setConfiguration(
          ConfigurationUtils.parseConfiguration(ApiFeConfigurationTest.class.getResource("/config.yml").getPath())
      );

      Injector injector = Guice.createInjector(
          apiFeModule,
          new ZookeeperModule(),
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          }
      );

      TestPaginationConfigInjection configWrapper = injector.getInstance(TestPaginationConfigInjection.class);
      assertThat(configWrapper.config.getDefaultPageSize(), is(10));
      assertThat(configWrapper.config.getMaxPageSize(), is(100));
    }
  }

  /**
   * Tests for injecting useVirtualNetwork.
   */
  public class TestUseVirtualNetworkConfig {

    @Test
    public void testUsingVirtualNetwork() throws Throwable {
      ApiFeModule apiFeModule = new ApiFeModule();
      apiFeModule.setConfiguration(
          ConfigurationUtils.parseConfiguration(ApiFeConfigurationTest.class.getResource("/config.yml").getPath())
      );

      Injector injector = Guice.createInjector(
          apiFeModule,
          new ZookeeperModule(),
          new AbstractModule() {
            @Override
            protected void configure() {
              bindScope(RequestScoped.class, Scopes.NO_SCOPE);
            }
          }
      );

      TestUseVirtualNetworkFlag configWrapper = injector.getInstance(TestUseVirtualNetworkFlag.class);
      assertThat(configWrapper.useVirtualNetwork, is(true));
    }
  }
}
