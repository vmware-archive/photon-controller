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

package com.vmware.photon.controller.deployer.helpers.dcp;

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.testng.Assert.assertTrue;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a test host for DCP micro-services.
 */
public class TestEnvironment extends MultiHostEnvironment<DeployerXenonServiceHost> {

  private static final Logger logger = LoggerFactory.getLogger(TestEnvironment.class);

  /**
   * Constructs a test environment object for various tests.
   *
   * @param deployerContext           Supplies the deployer context object.
   * @param containersConfig          Supplies the containers config object.
   * @param agentControlClientFactory Supplies the AgentControlClient factory object.
   * @param hostClientFactory         Supplies the HostClient factory object.
   * @param listeningExecutorService  Supplies the listening executor service object.
   * @param apiClientFactory          Supplies the API factory object.
   * @param dockerProvisionerFactory  Supplies the docker provisioner factory object.
   * @param authHelperFactory         Supplies the AuthHelper factory object.
   * @param hostCount                 Supplies the host count.
   * @param operationTimeoutMicros    Supplies the operation timeout value in microseconds.
   * @param hostNumber
   * @throws Throwable Throws an exception if any error is encountered.
   */
  private TestEnvironment(
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
      AgentControlClientFactory agentControlClientFactory,
      HostClientFactory hostClientFactory,
      HttpFileServiceClientFactory httpFileServiceClientFactory,
      ListeningExecutorService listeningExecutorService,
      ApiClientFactory apiClientFactory,
      DockerProvisionerFactory dockerProvisionerFactory,
      AuthHelperFactory authHelperFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      ZookeeperClientFactory zookeeperServerSetBuilderFactory,
      HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory,
      ClusterManagerFactory clusterManagerFactory,
      NsxClientFactory nsxClientFactory,
      int hostCount,
      Long operationTimeoutMicros,
      int hostNumber,
      ServerSet cloudServerSet) throws Throwable {

    assertTrue(hostCount > 0);

    hosts = new DeployerXenonServiceHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = Files.createTempDirectory(STORAGE_PATH_PREFIX).toAbsolutePath().toString();

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new DeployerXenonServiceHost(
          xenonConfig,
          cloudServerSet,
          deployerContext,
          containersConfig,
          agentControlClientFactory,
          hostClientFactory,
          httpFileServiceClientFactory,
          listeningExecutorService,
          apiClientFactory,
          dockerProvisionerFactory,
          authHelperFactory,
          healthCheckHelperFactory,
          serviceConfiguratorFactory,
          zookeeperServerSetBuilderFactory,
          hostManagementVmAddressValidatorFactory,
          clusterManagerFactory,
          nsxClientFactory);

      TaskSchedulerServiceStateBuilder.triggerInterval = TimeUnit.MILLISECONDS.toMicros(500);
      logger.debug(String.format("sandbox for %s: %s", hosts[i].getId(), sandbox));

      if (null != operationTimeoutMicros) {
        hosts[i].setOperationTimeOutMicros(operationTimeoutMicros);
      }
    }
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {

    private ApiClientFactory apiClientFactory;
    private AuthHelperFactory authHelperFactory;
    private ContainersConfig containersConfig;
    private DeployerContext deployerContext;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;
    private Integer hostCount;
    private ListeningExecutorService listeningExecutorService;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private Long operationTimeoutMicros;
    private int hostNumber;
    private ServerSet cloudServerSet;
    private ZookeeperClientFactory zookeeperServerSetBuilderFactory;
    private HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
    private ClusterManagerFactory clusterManagerFactory;
    private NsxClientFactory nsxClientFactory;

    public Builder apiClientFactory(ApiClientFactory apiClientFactory) {
      this.apiClientFactory = apiClientFactory;
      return this;
    }

    public Builder authHelperFactory(AuthHelperFactory authHelperFactory) {
      this.authHelperFactory = authHelperFactory;
      return this;
    }

    public Builder clusterManagerFactory(ClusterManagerFactory clusterManagerFactory) {
      this.clusterManagerFactory = clusterManagerFactory;
      return this;
    }

    public Builder containersConfig(ContainersConfig containersConfig) {
      this.containersConfig = containersConfig;
      return this;
    }

    public Builder deployerContext(DeployerContext deployerContext) {
      this.deployerContext = deployerContext;
      return this;
    }

    public Builder dockerProvisionerFactory(DockerProvisionerFactory dockerProvisionerFactory) {
      this.dockerProvisionerFactory = dockerProvisionerFactory;
      return this;
    }

    public Builder hostClientFactory(HostClientFactory hostClientFactory) {
      this.hostClientFactory = hostClientFactory;
      return this;
    }

    public Builder agentControlClientFactory(AgentControlClientFactory agentControlClientFactory) {
      this.agentControlClientFactory = agentControlClientFactory;
      return this;
    }

    public Builder healthCheckerFactory(HealthCheckHelperFactory healthCheckHelperFactory) {
      this.healthCheckHelperFactory = healthCheckHelperFactory;
      return this;
    }

    public Builder serviceConfiguratorFactory(ServiceConfiguratorFactory serviceConfiguratorFactory) {
      this.serviceConfiguratorFactory = serviceConfiguratorFactory;
      return this;
    }

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
      return this;
    }

    public Builder listeningExecutorService(ListeningExecutorService listeningExecutorService) {
      this.listeningExecutorService = listeningExecutorService;
      return this;
    }

    public Builder httpFileServiceClientFactory(HttpFileServiceClientFactory httpFileServiceClientFactory) {
      this.httpFileServiceClientFactory = httpFileServiceClientFactory;
      return this;
    }

    public Builder operationTimeoutMicros(long operationTimeoutMicros) {
      this.operationTimeoutMicros = operationTimeoutMicros;
      return this;
    }

    public Builder cloudServerSet(ServerSet cloudServerSet) {
      this.cloudServerSet = cloudServerSet;
      return this;
    }

    public Builder zookeeperServersetBuilderFactory(ZookeeperClientFactory zookeeperServerSetBuilderFactory) {
      this.zookeeperServerSetBuilderFactory = zookeeperServerSetBuilderFactory;
      return this;
    }

    public Builder hostManagementVmAddressValidatorBuilderFactory(
        HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory) {
      this.hostManagementVmAddressValidatorFactory = hostManagementVmAddressValidatorFactory;
      return this;
    }

    public Builder nsxClientFactory(NsxClientFactory nsxClientFactory) {
      this.nsxClientFactory = nsxClientFactory;
      return this;
    }

    public TestEnvironment build() throws Throwable {

      if (null == this.hostCount) {
        throw new IllegalArgumentException("Host count is required");
      }

      TestEnvironment testEnvironment = new TestEnvironment(
          this.deployerContext,
          this.containersConfig,
          this.agentControlClientFactory,
          this.hostClientFactory,
          this.httpFileServiceClientFactory,
          this.listeningExecutorService,
          this.apiClientFactory,
          this.dockerProvisionerFactory,
          this.authHelperFactory,
          this.healthCheckHelperFactory,
          this.serviceConfiguratorFactory,
          this.zookeeperServerSetBuilderFactory,
          this.hostManagementVmAddressValidatorFactory,
          this.clusterManagerFactory,
          this.nsxClientFactory,
          this.hostCount,
          this.operationTimeoutMicros,
          this.hostNumber,
          this.cloudServerSet);

      testEnvironment.start();
      return testEnvironment;
    }
  }
}
