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

import com.vmware.photon.controller.api.frontend.ApiFeService;
import com.vmware.photon.controller.api.frontend.clients.api.LocalApiClient;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.ssl.KeyStoreUtils;
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
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.housekeeper.xenon.HousekeeperServiceGroup;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerServiceGroup;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * PhotonControllerCore entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  public static final String CLUSTER_SCRIPTS_DIRECTORY = "clusters";

  public static void main(String[] args) throws Throwable {
    try {
    LoggingFactory.bootstrap();

    logger.info("args: " + Arrays.toString(args));

    ArgumentParser parser = ArgumentParsers.newArgumentParser("PhotonControllerCore")
        .defaultHelp(true)
        .description("Photon Controller Core");
    parser.addArgument("config-file").help("photon controller configuration file");
    parser.addArgument("--manual")
        .type(Boolean.class)
        .setDefault(false)
        .help("If true, create default deployment.");

    Namespace namespace = parser.parseArgsOrFail(args);

    PhotonControllerConfig photonControllerConfig = getPhotonControllerConfig(namespace);
    DeployerConfig deployerConfig = photonControllerConfig.getDeployerConfig();

    new LoggingFactory(photonControllerConfig.getLogging(), "photon-controller-core").configure();

    SSLContext sslContext;
    if (deployerConfig.getDeployerContext().isAuthEnabled()) {
      sslContext = SSLContext.getInstance(KeyStoreUtils.THRIFT_PROTOCOL);
      TrustManagerFactory tmf = null;

      tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyStore keyStore = KeyStore.getInstance("JKS");
      InputStream in = FileUtils.openInputStream(new File(deployerConfig.getDeployerContext().getKeyStorePath()));
      keyStore.load(in, deployerConfig.getDeployerContext().getKeyStorePassword().toCharArray());
      tmf.init(keyStore);
      sslContext.init(null, tmf.getTrustManagers(), null);
    } else {
      KeyStoreUtils.generateKeys("/thrift/");
      sslContext = KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);
    }

    ThriftModule thriftModule = new ThriftModule(sslContext);
    PhotonControllerXenonHost xenonHost = startXenonHost(photonControllerConfig, thriftModule, deployerConfig,
        sslContext);

    if ((Boolean) namespace.get("manual")) {
      DefaultDeployment.createDefaultDeployment(
          photonControllerConfig.getXenonConfig().getPeerNodes(),
          deployerConfig,
          xenonHost);
    }

    // Creating a temp configuration file for apife with modification to some named sections in photon-controller-config
    // so that it can match the Configuration class of dropwizard.
    File apiFeTempConfig = File.createTempFile("apiFeTempConfig", ".tmp");
    File source = new File(args[0]);
    FileInputStream fis = new FileInputStream(source);
    BufferedReader in = new BufferedReader(new InputStreamReader(fis));

    FileWriter fstream = new FileWriter(apiFeTempConfig, true);
    BufferedWriter out = new BufferedWriter(fstream);

    String aLine = null;
    while ((aLine = in.readLine()) != null) {
      if (aLine.equals("apife:")) {
        aLine = aLine.replace("apife:", "server:");
      }
      out.write(aLine);
      out.newLine();
    }
    in.close();
    out.close();

    // This approach can be simplified once the apife container is gone, but for the time being
    // it expects the first arg to be the string "server".
    String[] apiFeArgs = new String[2];
    apiFeArgs[0] = "server";
    apiFeArgs[1] = apiFeTempConfig.getAbsolutePath();
    ApiFeService.setupApiFeConfigurationForServerCommand(apiFeArgs);
    ApiFeService.addServiceHost(xenonHost);
    ApiFeService.setSSLContext(sslContext);

    ApiFeService apiFeService = new ApiFeService();
    apiFeService.run(apiFeArgs);
    apiFeTempConfig.deleteOnExit();

    LocalApiClient localApiClient = apiFeService.getInjector().getInstance(LocalApiClient.class);
    xenonHost.setApiClient(localApiClient);

    // in the non-auth enabled scenario we need to be able to accept any self-signed certificate
    if (!deployerConfig.getDeployerContext().isAuthEnabled()) {
      KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        xenonHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });
   } catch (Exception e) {
      logger.error("Failed to start photon controller ", e);
      throw e;
     }
  }

  private static PhotonControllerXenonHost startXenonHost(PhotonControllerConfig photonControllerConfig,
                                            ThriftModule thriftModule,
                                            DeployerConfig deployerConfig,
                                            SSLContext sslContext) throws Throwable {
    // Values for CloudStore
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    // Values for Scheduler
    final ServerSet cloudStoreServerSet =
        new StaticServerSet(new InetSocketAddress(photonControllerConfig.getXenonConfig().getRegistrationAddress(),
            Constants.PHOTON_CONTROLLER_PORT));
    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);


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

    ServerSet apiFeServerSet = new StaticServerSet(new InetSocketAddress(
        photonControllerConfig.getXenonConfig().getRegistrationAddress(), Constants.MANAGEMENT_API_PORT));

    logger.info("Creating PhotonController Xenon Host");
    final PhotonControllerXenonHost photonControllerXenonHost = new PhotonControllerXenonHost(
        photonControllerConfig.getXenonConfig(),
        hostClientFactory,
        agentControlClientFactory,
        nsxClientFactory,
        cloudStoreHelper,
        sslContext);
    logger.info("Created PhotonController Xenon Host");

    // Set referer Uri from the xenon host, because we do not want to rely on
    // CloudStoreHelper's default mechanise to create referer based on local address,
    // because CloudStoreHelper uses InetAddress.getLocalHost() which depends on
    // /etc/hosts having a hostname entry, which is not always available.
    // This change will allow people to run this service without need to
    // update their /etc/hosts file.
    cloudStoreHelper.setRefererUri(photonControllerXenonHost.getUri());

    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper, photonControllerXenonHost);

    logger.info("Creating Cloud Store Xenon Service Group");
    CloudStoreServiceGroup cloudStoreServiceGroup = createCloudStoreServiceGroup(deployerConfig.isInstaller());
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

    DeployerContext deployerContext = deployerConfig.getDeployerContext();
    if (deployerContext.isAuthEnabled()) {
      ServiceClient serviceClient = NettyHttpServiceClient.create(
          Main.class.getSimpleName(),
          Executors.newFixedThreadPool(Utils.DEFAULT_THREAD_COUNT),
          Executors.newScheduledThreadPool(Utils.DEFAULT_IO_THREAD_COUNT),
          photonControllerXenonHost);


      /*
      To make sure that Xenon uses only TLSv1.2 and disallows SSLv3, TLSv1,
      TLSv1.1 the Docker file for the photon-controller-core container is edited.
      The java.security file located inside the container at the location
      /var/opt/OpenJDK-* /jre/lib/security has the information under the
      jdk.tls.disabledAlgorithms
      */

      SSLContext clientContext = SSLContext.getInstance(ServiceClient.TLS_PROTOCOL_NAME);
      TrustManagerFactory trustManagerFactory = TrustManagerFactory
          .getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      KeyStore keyStore = KeyStore.getInstance("JKS");
      try (FileInputStream fis = new FileInputStream(deployerContext.getKeyStorePath())) {
        keyStore.load(fis, deployerContext.getKeyStorePassword().toCharArray());
      }
      keyManagerFactory.init(keyStore, deployerContext.getKeyStorePassword().toCharArray());
      clientContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
      serviceClient.setSSLContext(clientContext);
      photonControllerXenonHost.setClient(serviceClient);
    }

    logger.info("Starting PhotonController Xenon Host");
    photonControllerXenonHost.start();
    logger.info("Started PhotonController Xenon Host");

    logger.info("Creating SystemConfig instance");
    SystemConfig.createInstance(photonControllerXenonHost);
    logger.info("Created SystemConfig instance");
    return photonControllerXenonHost;
  }

  private static CloudStoreServiceGroup createCloudStoreServiceGroup(boolean isInstaller) throws Throwable {
    return new CloudStoreServiceGroup(isInstaller);
  }

  private static SchedulerServiceGroup createSchedulerServiceGroup(SchedulerConfig root,
          ConstraintChecker constraintChecker) throws Throwable {
    return new SchedulerServiceGroup(root, constraintChecker);
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

    final ApiClientFactory apiClientFactory = new ApiClientFactory(apiFeServerSet, httpClient,
        deployerConfig.getDeployerContext().getSharedSecret(), deployerConfig.getDeployerContext().isAuthEnabled());

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
    final ZookeeperClientFactory zookeeperServerSetBuilderFactory =
        new com.vmware.photon.controller.core.Main.ZookeeperClientFactoryImpl();
    final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory = new
        com.vmware.photon.controller.core.Main.HostManagementVmAddressValidatorFactoryImpl();

    final ClusterManagerFactory clusterManagerFactory = new ClusterManagerFactory(listeningExecutorService,
        httpClient, cloudStoreServerSet,
        Paths.get(deployerConfig.getDeployerContext().getScriptDirectory(), CLUSTER_SCRIPTS_DIRECTORY).toString());

    return new DeployerServiceGroup(deployerConfig.getDeployerContext(),
        apiClientFactory, deployerConfig.getContainersConfig(), listeningExecutorService,
        httpFileServiceClientFactory, authHelperFactory, healthCheckHelperFactory,
        serviceConfiguratorFactory, zookeeperServerSetBuilderFactory, hostManagementVmAddressValidatorFactory,
        clusterManagerFactory);
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
                                    final String ipAddress, boolean authEnabled) {
      return new HealthCheckHelper(service, containerType, ipAddress, authEnabled);
    }

    @Override
    public XenonBasedHealthChecker create(final Service service, final String protocol, final Integer port,
                                          final String ipAddress) {
      return new XenonBasedHealthChecker(service, protocol, ipAddress, port);
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

    private ZookeeperClientFactoryImpl() {
    }

    @Override
    public ZookeeperClient create() {
      return new ZookeeperClient(null);
    }
  }
}
