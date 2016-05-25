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
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.XenonBasedHealthChecker;
import com.vmware.photon.controller.deployer.service.DeployerService;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;
import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class implements the entry point for the deployer service.
 */
public class Main {

  public static final String CLUSTER_SCRIPTS_DIRECTORY = "clusters";

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /**
   * This method provides the main entry point for the deployer service.
   *
   * @param args
   * @throws Throwable
   */
  public static void main(String[] args) throws Throwable {

    try {

      LoggingFactory.bootstrap();

      ArgumentParser parser = ArgumentParsers.newArgumentParser("Deployer")
          .defaultHelp(true)
          .description("Photon Controller Deployer");
      parser.addArgument("file").help("configuration file");

      logger.info("Parsing command-line arguments");
      Namespace namespace = parser.parseArgsOrFail(args);

      logger.info("Parsing service configuration file");
      DeployerConfig deployerConfig = ConfigBuilder.build(DeployerConfig.class, namespace.getString("file"));

      logger.info("Configuring logging");
      new LoggingFactory(deployerConfig.getLogging(), "deployer").configure();

      logger.info("Creating Guice injector");
      Injector injector = Guice.createInjector(
          new DeployerModule(deployerConfig),
          new ZookeeperModule(deployerConfig.getZookeeper()),
          new ThriftModule()
      );

      ZookeeperServerSetFactory serverSetFactory = injector.getInstance(ZookeeperServerSetFactory.class);
      ServerSet deployerServerSet = serverSetFactory.createServiceServerSet(Constants.DEPLOYER_SERVICE_NAME, true);
      ServerSet cloudStoreServerSet = serverSetFactory.createServiceServerSet(Constants.CLOUDSTORE_SERVICE_NAME, true);
      ServerSet apiFeServerSet = serverSetFactory.createServiceServerSet(Constants.APIFE_SERVICE_NAME, true);

      final CloseableHttpAsyncClient httpClient;
      try {
        SSLContext sslcontext = SSLContexts.custom()
            .loadTrustMaterial((chain, authtype) -> true)
            .build();
        httpClient = HttpAsyncClientBuilder.create()
            .setHostnameVerifier(SSLIOSessionStrategy.ALLOW_ALL_HOSTNAME_VERIFIER)
            .setSSLContext(sslcontext)
            .build();
        httpClient.start();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }

      final ThriftModule thriftModule = injector.getInstance(ThriftModule.class);

      final DeployerXenonServiceHost deployerXenonServiceHost = createDeployerXenonServiceHost(thriftModule,
          deployerConfig, apiFeServerSet, cloudStoreServerSet, httpClient);

      final DeployerService deployerService = createDeployerService(injector, deployerXenonServiceHost,
          deployerServerSet);

      final DeployerServer thriftServer = createDeployerServer(injector, deployerService, deployerConfig, httpClient);

      logger.info("Adding shutdown hook");
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          logger.info("Shutting down");
          thriftServer.stop();
          deployerXenonServiceHost.stop();
          logger.info("Done");
          LoggingFactory.detachAndStop();
        }
      });

      logger.info("Starting Deployer Xenon host service");
      deployerXenonServiceHost.start();

      logger.info("Starting Thrift service");
      thriftServer.serve();

    } catch (Throwable t) {
      t.printStackTrace();
      logger.error(t.getMessage());
      System.exit(1);
    }
  }

  /**
   * Creates a new DeployerXenonServiceHost.
   *
   * @param thriftModule
   * @param deployerConfig
   * @param cloudStoreServerSet
   * @return
   * @throws Throwable
   */
  private static DeployerXenonServiceHost createDeployerXenonServiceHost(ThriftModule thriftModule,
                                                                         DeployerConfig deployerConfig,
                                                                         ServerSet apiFeServerSet,
                                                                         ServerSet cloudStoreServerSet,
                                                                         CloseableHttpAsyncClient httpClient)
      throws Throwable {
    logger.info("Creating Deployer Xenon host instance");
    // Set deployer context zookeeper quorum
    deployerConfig.getDeployerContext().setZookeeperQuorum(deployerConfig.getZookeeper().getQuorum());

    // Set containers config to deployer config
    try {
      deployerConfig.setContainersConfig(new ServiceConfigurator().generateContainersConfig(deployerConfig
          .getDeployerContext().getConfigDirectory()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    /**
     * The blocking queue associated with the thread pool executor service
     * controls the rejection policy for new work items: a bounded queue, such as
     * an ArrayBlockingQueue, will cause new work items to be rejected (and thus
     * failed) when the queue length is reached. A LinkedBlockingQueue, which is
     * unbounded, is used here in order to enable the submission of an arbitrary
     * number of work items since this is the pattern expected for the deployer
     * (a large number of work items arrive all at once, and then no more).
     */
    final BlockingQueue<Runnable> blockingQueue = new LinkedBlockingDeque<>();
    final ListeningExecutorService listeningExecutorService = MoreExecutors.listeningDecorator(
        new ThreadPoolExecutor(
            deployerConfig.getDeployerContext().getCorePoolSize(),
            deployerConfig.getDeployerContext().getMaximumPoolSize(),
            deployerConfig.getDeployerContext().getKeepAliveTime(),
            TimeUnit.SECONDS,
            blockingQueue));

    final DockerProvisionerFactory dockerProvisionerFactory = new DockerProvisionerFactoryImpl();
    final AuthHelperFactory authHelperFactory = new AuthHelperFactoryImpl();
    final HealthCheckHelperFactory healthCheckHelperFactory = new HealthCheckHelperFactoryImpl();
    final ServiceConfiguratorFactory serviceConfiguratorFactory = new ServiceConfiguratorFactoryImpl();
    final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory = new
        HostManagementVmAddressValidatorFactoryImpl();
    final HttpFileServiceClientFactory httpFileServiceClientFactory = new HttpFileServiceClientFactoryImpl();
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    final ZookeeperClientFactory zookeeperServerSetBuilderFactory = new ZookeeperClientFactoryImpl(deployerConfig);

    final ApiClientFactory apiClientFactory = new ApiClientFactory(apiFeServerSet, httpClient,
        deployerConfig.getDeployerContext().getSharedSecret());

    final ClusterManagerFactory clusterManagerFactory = new ClusterManagerFactory(listeningExecutorService,
        httpClient, apiFeServerSet, deployerConfig.getDeployerContext().getSharedSecret(), cloudStoreServerSet,
        Paths.get(deployerConfig.getDeployerContext().getScriptDirectory(), CLUSTER_SCRIPTS_DIRECTORY).toString());

    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();

    return new DeployerXenonServiceHost(deployerConfig.getXenonConfig(), cloudStoreServerSet,
        deployerConfig.getDeployerContext(), deployerConfig.getContainersConfig(), agentControlClientFactory,
        hostClientFactory, httpFileServiceClientFactory, listeningExecutorService, apiClientFactory,
        dockerProvisionerFactory, authHelperFactory, healthCheckHelperFactory, serviceConfiguratorFactory,
        zookeeperServerSetBuilderFactory, hostManagementVmAddressValidatorFactory, clusterManagerFactory,
        nsxClientFactory);
  }

  /**
   * Creates a new DeployerService.
   *
   * @param injector
   * @param deployerXenonServiceHost
   * @param deployerServerSet
   * @return
   */
  private static DeployerService createDeployerService(Injector injector,
                                                       DeployerXenonServiceHost deployerXenonServiceHost,
                                                       ServerSet deployerServerSet) {
    final HostServiceClientFactory hostServiceClientFactory = injector.getInstance(HostServiceClientFactory.class);
    final ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory = injector.getInstance
        (ChangeHostModeTaskServiceClientFactory.class);
    final DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory = injector.getInstance
        (DeploymentWorkflowServiceClientFactory.class);
    final AddHostWorkflowServiceClientFactory addHostWorkflowServiceClientFactory = injector.getInstance
        (AddHostWorkflowServiceClientFactory.class);
    final ValidateHostTaskServiceClientFactory validateHostTaskServiceClientFactory = injector.getInstance
        (ValidateHostTaskServiceClientFactory.class);
    final DeprovisionHostWorkflowServiceClientFactory deprovisionHostClientFactory = injector
        .getInstance(DeprovisionHostWorkflowServiceClientFactory.class);

    return new DeployerService(deployerServerSet, deployerXenonServiceHost,
        hostServiceClientFactory, changeHostModeTaskServiceClientFactory, deploymentWorkflowServiceClientFactory,
        addHostWorkflowServiceClientFactory, validateHostTaskServiceClientFactory, deprovisionHostClientFactory);
  }

  /**
   * Creates a new DeployerServer.
   *
   * @param injector
   * @param deployerService
   * @param deployerConfig
   * @return
   */
  private static DeployerServer createDeployerServer(Injector injector, DeployerService deployerService,
                                                     DeployerConfig deployerConfig,
                                                     CloseableHttpAsyncClient httpClient) {
    logger.info("Creating Thrift server instance");
    final ServiceNodeFactory serviceNodeFactory = injector.getInstance(ServiceNodeFactory.class);
    final TProtocolFactory protocolFactory = injector.getInstance(TProtocolFactory.class);
    final TTransportFactory transportFactory = injector.getInstance(TTransportFactory.class);
    final ThriftFactory thriftFactory = injector.getInstance(ThriftFactory.class);

    return new DeployerServer(serviceNodeFactory, protocolFactory, transportFactory,
        thriftFactory, deployerService, deployerConfig.getThriftConfig(), httpClient);
  }

  /**
   * Implementation of DockerProvisionerFactory.
   */
  private static class DockerProvisionerFactoryImpl implements DockerProvisionerFactory {

    @Override
    public DockerProvisioner create(String dockerEndpoint) {
      return new DockerProvisioner(dockerEndpoint);
    }
  }

  /**
   * Implementation of AuthHelperFactory.
   */
  private static class AuthHelperFactoryImpl implements AuthHelperFactory {

    @Override
    public AuthHelper create() {
      return new AuthHelper();
    }
  }

  /**
   * Implementation of HealthCheckHelperFactory.
   */
  private static class HealthCheckHelperFactoryImpl implements HealthCheckHelperFactory {

    @Override
    public HealthCheckHelper create(final Service service, final ContainersConfig.ContainerType containerType,
                                    final String ipAddress) {
      return new HealthCheckHelper(service, containerType, ipAddress);
    }

    @Override
    public XenonBasedHealthChecker create(final Service service, final Integer port, final String ipAddress) {
      return new XenonBasedHealthChecker(service, ipAddress, port);
    }
  }

  /**
   * Implementation of ServiceConfiguratorFactory.
   */
  private static class ServiceConfiguratorFactoryImpl implements ServiceConfiguratorFactory {

    @Override
    public ServiceConfigurator create() {
      return new ServiceConfigurator();
    }
  }

  /**
   * Implementation of HostManagementVmAddressValidatorFactory.
   */
  private static class HostManagementVmAddressValidatorFactoryImpl implements HostManagementVmAddressValidatorFactory {

    @Override
    public HostManagementVmAddressValidator create(String hostAddress) {
      return new HostManagementVmAddressValidator(hostAddress);
    }
  }

  /**
   * Implementation of HttpFileServiceClientFactory.
   */
  private static class HttpFileServiceClientFactoryImpl implements HttpFileServiceClientFactory {

    @Override
    public HttpFileServiceClient create(String hostAddress,
                                        String userName,
                                        String password) {
      return new HttpFileServiceClient(hostAddress, userName, password);
    }
  }

  /**
   * Implementation of ZookeeperClientFactory.
   */
  private static class ZookeeperClientFactoryImpl implements ZookeeperClientFactory {
    private final DeployerConfig deployerConfig;

    private ZookeeperClientFactoryImpl(final DeployerConfig deployerConfig) {
      this.deployerConfig = deployerConfig;
    }

    @Override
    public ZookeeperClient create() {
      /*deployerConfig.getZookeeper().getNamespace()*/
      return new ZookeeperClient(deployerConfig.getZookeeper().getNamespace());
    }
  }
}
