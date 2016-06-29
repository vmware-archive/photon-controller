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

import com.vmware.photon.controller.apife.ApiFeService;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
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
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.XenonBasedHealthChecker;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.housekeeper.xenon.HousekeeperServiceGroup;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
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
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
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
    parser.addArgument("api-config-file").help("photon controller api configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    PhotonControllerConfig photonControllerConfig = getPhotonControllerConfig(namespace);
    DeployerConfig deployerConfig = photonControllerConfig.getDeployerConfig();

    new LoggingFactory(photonControllerConfig.getLogging(), "photon-controller-core").configure();

    ThriftModule thriftModule = new ThriftModule();

    ServiceHost xenonHost = startXenonHost(photonControllerConfig, thriftModule, deployerConfig);

    // This approach can be simplified once the apife container is gone, but for the time being
    // it expects the first arg to be the string "server".
    String[] apiFeArgs = new String[2];
    apiFeArgs[0] = "server";
    apiFeArgs[1] = args[1];
    ApiFeService.setupApiFeConfigurationForServerCommand(apiFeArgs);
    new ApiFeService().run(apiFeArgs);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        xenonHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });
  }

  private static ServiceHost startXenonHost(PhotonControllerConfig photonControllerConfig,
                                            ThriftModule thriftModule, DeployerConfig deployerConfig) throws Throwable {
    // Values for CloudStore
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    // Values for Scheduler
    final ServerSet cloudStoreServerSet =
        new StaticServerSet(new InetSocketAddress("127.0.0.1", Constants.PHOTON_CONTROLLER_PORT));
    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

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

    ServerSet apiFeServerSet = new StaticServerSet(new InetSocketAddress("127.0.0.1", Constants.MANAGEMENT_API_PORT));

    logger.info("Creating PhotonController Xenon Host");
    final PhotonControllerXenonHost photonControllerXenonHost =
            new PhotonControllerXenonHost(photonControllerConfig.getXenonConfig(),
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
            createSchedulerServiceGroup(photonControllerConfig.getRoot(), checker);
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

    logger.info("Creating Deployer Xenon Service Group");
    DeployerServiceGroup deployerServiceGroup = createDeployerServiceGroup(photonControllerConfig,
        deployerConfig, apiFeServerSet, cloudStoreServerSet, httpClient);
    logger.info("Created Deployer Xenon Service Group");

    logger.info("Registering Deployer Xenon Service Group");
    photonControllerXenonHost.registerDeployer(deployerServiceGroup);
    logger.info("Registered Deployer Xenon Service Group");

    logger.info("Starting PhotonController Xenon Host");
    photonControllerXenonHost.start();
    logger.info("Started PhotonController Xenon Host");

    logger.info("Creating SystemConfig instance");
    SystemConfig.createInstance(photonControllerXenonHost);
    logger.info("Created SystemConfig instance");
    return photonControllerXenonHost;
  }



  private static CloudStoreServiceGroup createCloudStoreServiceGroup() throws Throwable {
    return new CloudStoreServiceGroup();
  }

  private static SchedulerServiceGroup createSchedulerServiceGroup(SchedulerConfig root,
          ConstraintChecker constraintChecker) throws Throwable {
    return  new SchedulerServiceGroup(root, constraintChecker);
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

  /**
   * Creates a new Deployer Service Group.
   *
   * @param deployerConfig
   * @param apiFeServerSet
   * @param cloudStoreServerSet
   * @param httpClient
   * @return
   */
  private static DeployerServiceGroup createDeployerServiceGroup (PhotonControllerConfig photonControllerConfig,
                                                                  DeployerConfig deployerConfig,
                                                                  ServerSet apiFeServerSet,
                                                                  ServerSet cloudStoreServerSet,
                                                                  CloseableHttpAsyncClient httpClient) {

    logger.info("Creating Deployer Service Group");

    // Set containers config to deployer config
    try {
      deployerConfig.setContainersConfig(new ServiceConfigurator().generateContainersConfig(deployerConfig
          .getDeployerContext().getConfigDirectory()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final DockerProvisionerFactory dockerProvisionerFactory = new com.vmware.photon.controller.core.Main
        .DockerProvisionerFactoryImpl();
    final ApiClientFactory apiClientFactory = new ApiClientFactory(apiFeServerSet, httpClient,
        deployerConfig.getDeployerContext().getSharedSecret());

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

    final HttpFileServiceClientFactory httpFileServiceClientFactory = new com.vmware.photon.controller.core.Main
        .HttpFileServiceClientFactoryImpl();
    final AuthHelperFactory authHelperFactory = new com.vmware.photon.controller.core.Main.AuthHelperFactoryImpl();
    final HealthCheckHelperFactory healthCheckHelperFactory =
        new com.vmware.photon.controller.core.Main.HealthCheckHelperFactoryImpl();
    final ServiceConfiguratorFactory serviceConfiguratorFactory =
        new com.vmware.photon.controller.core.Main.ServiceConfiguratorFactoryImpl();

    final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory = new
        com.vmware.photon.controller.core.Main.HostManagementVmAddressValidatorFactoryImpl();

    final ClusterManagerFactory clusterManagerFactory = new ClusterManagerFactory(listeningExecutorService,
        httpClient, apiFeServerSet, deployerConfig.getDeployerContext().getSharedSecret(), cloudStoreServerSet,
        Paths.get(deployerConfig.getDeployerContext().getScriptDirectory(), CLUSTER_SCRIPTS_DIRECTORY).toString());


    return new DeployerServiceGroup(deployerConfig.getDeployerContext(), dockerProvisionerFactory,
        apiClientFactory, deployerConfig.getContainersConfig(), listeningExecutorService,
        httpFileServiceClientFactory, authHelperFactory, healthCheckHelperFactory,
        serviceConfiguratorFactory, null, hostManagementVmAddressValidatorFactory,
        clusterManagerFactory);
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
}
