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

package com.vmware.photon.controller.core;

import com.vmware.photon.controller.cloudstore.CloudStoreConfig;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.XenonBasedHealthChecker;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerXenonServiceHost;
import com.vmware.photon.controller.housekeeper.xenon.HousekeeperServiceGroup;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerServiceGroup;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * PhotonControllerCore entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMillis = TimeUnit.SECONDS.toMillis(30);
  public static final String CLUSTER_SCRIPTS_DIRECTORY = "clusters";

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    logger.info("args: " + Arrays.toString(args));

    ArgumentParser parser = ArgumentParsers.newArgumentParser("PhotonControllerCore")
        .defaultHelp(true)
        .description("Photon Controller Core");
    parser.addArgument("config-file").help("photon controller configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    PhotonControllerConfig photonControllerConfig = getPhotonControllerConfig(namespace);
    CloudStoreConfig cloudStoreConfig = photonControllerConfig.getCloudStoreConfig();
    DeployerConfig deployerConfig = photonControllerConfig.getDeployerConfig();

    new LoggingFactory(photonControllerConfig.getLogging(), "photon-controller-core").configure();

    // the zk config info is currently taken from the cloud store config but this is temporary as
    // zookeeper usage is going away so this will all be removed.
    final ZookeeperModule zkModule = new ZookeeperModule(cloudStoreConfig.getZookeeper());
    ThriftModule thriftModule = new ThriftModule();

    ServiceHost xenonHost = startXenonHost(photonControllerConfig, zkModule, thriftModule);
    ServiceHost deployerHost = startDeployer(deployerConfig, zkModule, thriftModule);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        deployerHost.stop();
        xenonHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });
  }

  private static ServiceHost startXenonHost(PhotonControllerConfig photonControllerConfig, ZookeeperModule zkModule,
                                            ThriftModule thriftModule) throws Throwable {
    final CuratorFramework zkClient = zkModule.getCuratorFramework();

    // Values for CloudStore
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    // Values for Scheduler
    final ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient,
            Constants.CLOUDSTORE_SERVICE_NAME, true);
    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    logger.info("Creating PhotonController Xenon Host");
    final PhotonControllerXenonHost photonControllerXenonHost =
            new PhotonControllerXenonHost(photonControllerConfig.getCloudStoreConfig().getXenonConfig(),
                hostClientFactory, agentControlClientFactory, nsxClientFactory, cloudStoreHelper);
    logger.info("Created PhotonController Xenon Host");

    logger.info("Creating Cloud Store Xenon Service Group");
    CloudStoreServiceGroup cloudStoreServiceGroup = createCloudStoreServiceGroup();
    logger.info("Created Cloud Store Xenon Service Group");

    logger.info("Registering Cloud Store Xenon Service Group");
    photonControllerXenonHost.registerCloudStore(cloudStoreServiceGroup);
    logger.info("Registered Cloud Store Xenon Service Group");

    logger.info("Creating Scheduler Xenon Service Group");
    SchedulerServiceGroup schedulerServiceGroup =
            createSchedulerServiceGroup(photonControllerConfig.getSchedulerConfig(), checker);
    logger.info("Created Scheduler Xenon Service Group");

    logger.info("Registering Scheduler Xenon Service Group");
    photonControllerXenonHost.registerScheduler(schedulerServiceGroup);
    logger.info("Registered Scheduler Xenon Service Group");

    logger.info("Creating Housekeeper Xenon Service Group");
    HousekeeperServiceGroup housekeeperServiceGroup = createHousekeeperServiceGroup();
    logger.info("Created Housekeeper Xenon Service Group");

    logger.info("Registering Housekeeper Xenon Service Group");
    photonControllerXenonHost.registerHousekeeper(housekeeperServiceGroup);
    logger.info("Registered Housekeeper Xenon Service Group");

    logger.info("Starting PhotonController Xenon Host");
    photonControllerXenonHost.start();
    logger.info("Started PhotonController Xenon Host");

    // For now we register cloudstore, scheduler and housekeeper services with zookeeper so that users of
    // the services don't need to change their lookup / usage behavior but now we use the same
    // address / port for all these(for now the cloudstore address / port)
    String cloudStoreXenonAddress =
            photonControllerConfig.getCloudStoreConfig().getXenonConfig().getRegistrationAddress();
    Integer cloudStoreXenonPort = photonControllerConfig.getCloudStoreConfig().getXenonConfig().getPort();
    logger.info("Registering CloudStore Services Endpoint with Zookeeper at {}:{}",
            cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.CLOUDSTORE_SERVICE_NAME, zkModule, zkClient,
            cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered CloudStore Services Endpoint with Zookeeper");

    logger.info("Registering Scheduler Services Endpoint with Zookeeper at {}:{}",
            cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.SCHEDULER_SERVICE_NAME, zkModule, zkClient,
            cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered Scheduler Services Endpoint with Zookeeper");

    logger.info("Registering Housekeeper Services Endpoint with Zookeeper at {}:{}",
        cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.HOUSEKEEPER_SERVICE_NAME, zkModule, zkClient,
        cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered Scheduler Services Endpoint with Zookeeper");

    return photonControllerXenonHost;
  }

  private static ServiceHost startDeployer(DeployerConfig deployerConfig, ZookeeperModule zkModule,
                                           ThriftModule thriftModule) throws Throwable {
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    Integer deployerXenonPort = deployerConfig.getXenonConfig().getPort();
    String deployerXenonAddress = deployerConfig.getXenonConfig().getRegistrationAddress();

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

    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.CLOUDSTORE_SERVICE_NAME, true);
    ServerSet apiFeServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.APIFE_SERVICE_NAME, true);

    logger.info("Creating Deployer Xenon Host");
    final DeployerXenonServiceHost deployerXenonServiceHost = createDeployerXenonServiceHost(thriftModule,
            deployerConfig, apiFeServerSet, cloudStoreServerSet, httpClient);
    logger.info("Created Deployer Xenon Host");

    logger.info("Starting Deployer Xenon Host");
    deployerXenonServiceHost.start();
    logger.info("Started Deployer Xenon Host");

    logger.info("Registering Deployer Xenon Host with Zookeeper at {}:{}",
            deployerXenonAddress, deployerXenonPort);
    registerServiceWithZookeeper(Constants.DEPLOYER_SERVICE_NAME, zkModule, zkClient,
            deployerXenonAddress, deployerXenonPort);
    logger.info("Registered Deployer Xenon Host with Zookeeper");

    return deployerXenonServiceHost;
  }

  private static CloudStoreServiceGroup createCloudStoreServiceGroup() throws Throwable {
    return new CloudStoreServiceGroup();
  }

  private static SchedulerServiceGroup createSchedulerServiceGroup(RootSchedulerConfig rootSchedulerConfig,
          ConstraintChecker constraintChecker) throws Throwable {
    return  new SchedulerServiceGroup(rootSchedulerConfig.getRoot(), constraintChecker);
  }

  private static HousekeeperServiceGroup createHousekeeperServiceGroup() throws Throwable {
    return new HousekeeperServiceGroup();
  }

  private static PhotonControllerConfig getPhotonControllerConfig(Namespace namespace) {
    PhotonControllerConfig config = null;
    try {
      config = ConfigBuilder.build(PhotonControllerConfig.class, namespace.getString("config-file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }

  private static void registerServiceWithZookeeper(String serviceName, ZookeeperModule zkModule,
                                                   CuratorFramework zkClient, String ipAddress, int port) {
    zkModule.registerWithZookeeper(zkClient, serviceName, ipAddress, port, retryIntervalMillis);
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

    final DockerProvisionerFactory dockerProvisionerFactory = new com.vmware.photon.controller.core.Main
        .DockerProvisionerFactoryImpl();
    final AuthHelperFactory authHelperFactory = new com.vmware.photon.controller.core.Main.AuthHelperFactoryImpl();
    final HealthCheckHelperFactory healthCheckHelperFactory =
        new com.vmware.photon.controller.core.Main.HealthCheckHelperFactoryImpl();
    final ServiceConfiguratorFactory serviceConfiguratorFactory =
        new com.vmware.photon.controller.core.Main.ServiceConfiguratorFactoryImpl();
    final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory = new
            com.vmware.photon.controller.core.Main.HostManagementVmAddressValidatorFactoryImpl();
    final HttpFileServiceClientFactory httpFileServiceClientFactory = new com.vmware.photon.controller.core.Main
        .HttpFileServiceClientFactoryImpl();
    final com.vmware.photon.controller.deployer.deployengine.NsxClientFactory nsxClientFactory =
        new com.vmware.photon.controller.deployer.deployengine.NsxClientFactory();

    final ZookeeperClientFactory zookeeperServerSetBuilderFactory = new com.vmware.photon.controller.core.Main
        .ZookeeperClientFactoryImpl(deployerConfig);

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
      return new ZookeeperClient(deployerConfig.getZookeeper().getNamespace());
    }
  }
}
