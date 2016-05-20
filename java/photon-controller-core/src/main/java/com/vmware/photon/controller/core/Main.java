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
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.housekeeper.HousekeeperConfig;
import com.vmware.photon.controller.housekeeper.HousekeeperServer;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PhotonControllerCore entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMillis = TimeUnit.SECONDS.toMillis(30);

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
    RootSchedulerConfig schedulerConfig = photonControllerConfig.getSchedulerConfig();
    HousekeeperConfig housekeeperConfig = photonControllerConfig.getHousekeeperConfig();

    new LoggingFactory(photonControllerConfig.getLogging(), "photon-controller-core").configure();

    final ZookeeperModule zkModule = new ZookeeperModule(cloudStoreConfig.getZookeeper());
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    ThriftModule thriftModule = new ThriftModule();

    List<ServiceHost> serviceHosts = new ArrayList<>();
    ServiceHost cloudStoreHost = startCloudStore(cloudStoreConfig, zkModule, zkClient, thriftModule);
    serviceHosts.add(cloudStoreHost);
    ServiceHost schedulerHost = startScheduler(schedulerConfig, zkModule, zkClient, thriftModule);
    serviceHosts.add(schedulerHost);
    ServiceHost housekeeperHost = startHousekeeper(housekeeperConfig, zkModule, zkClient);
    serviceHosts.add(housekeeperHost);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        for (ServiceHost serviceHost : serviceHosts) {
          serviceHost.stop();
        }
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });
  }

  private static ServiceHost startCloudStore(CloudStoreConfig cloudStoreConfig, ZookeeperModule zkModule,
                                             CuratorFramework zkClient, ThriftModule thriftModule) throws Throwable {
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();

    logger.info("Creating CloudStore Xenon Host");
    final CloudStoreXenonHost cloudStoreXenonHost = new CloudStoreXenonHost(cloudStoreConfig.getXenonConfig(),
            hostClientFactory, agentControlClientFactory, serviceConfigFactory);
    logger.info("Created CloudStore Xenon Host");

    logger.info("Starting CloudStore Xenon Host");
    cloudStoreXenonHost.start();
    logger.info("Started CloudStore Xenon Host");

    String cloudStoreXenonAddress = cloudStoreConfig.getXenonConfig().getRegistrationAddress();
    Integer cloudStoreXenonPort = cloudStoreConfig.getXenonConfig().getPort();
    logger.info("Registering CloudStore Xenon Host with Zookeeper at {}:{}",
            cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.CLOUDSTORE_SERVICE_NAME, zkModule, zkClient,
            cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered CloudStore Xenon Host with Zookeeper");

    return cloudStoreXenonHost;
  }

  private static ServiceHost startHousekeeper(HousekeeperConfig housekeeperConfig, ZookeeperModule zkModule,
                                              CuratorFramework zkClient) throws Throwable {
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.CLOUDSTORE_SERVICE_NAME, true);
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);

    Injector injector = Guice.createInjector(
            new ThriftModule()
    );
    final ThriftModule thriftModule = injector.getInstance(ThriftModule.class);
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final TProtocolFactory tProtocolFactory = injector.getInstance(TProtocolFactory.class);
    final TTransportFactory tTransportFactory = injector.getInstance(TTransportFactory.class);
    final ThriftFactory thriftFactory = injector.getInstance(ThriftFactory.class);

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();




    logger.info("Creating Housekeeper Xenon Host");
    final HousekeeperXenonServiceHost housekeeperXenonHost = new HousekeeperXenonServiceHost(
            housekeeperConfig.getXenonConfig(), cloudStoreHelper, hostClientFactory, serviceConfigFactory,
            nsxClientFactory);

    ServerSet housekeeperServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.HOUSEKEEPER_SERVICE_NAME, true);
    final HousekeeperService housekeeperService = new HousekeeperService(housekeeperServerSet,
            housekeeperXenonHost);

    final HousekeeperServer thriftServer = new HousekeeperServer(zkModule, zkClient, tProtocolFactory,
            tTransportFactory, thriftFactory, housekeeperService, housekeeperConfig.getThriftConfig());
    logger.info("Created Housekeeper Xenon Host");

    logger.info("Starting Housekeeper Xenon Host");
    housekeeperXenonHost.start();
    thriftServer.serve();
    logger.info("Started Housekeeper Xenon Host");

    return housekeeperXenonHost;
  }

  private static ServiceHost startScheduler(RootSchedulerConfig schedulerConfig, ZookeeperModule zkModule,
                                            CuratorFramework zkClient, ThriftModule thriftModule) throws Throwable {
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.CLOUDSTORE_SERVICE_NAME, true);
    HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    logger.info("Creating Scheduler Xenon Host");
    final SchedulerXenonHost schedulerXenonHost = new SchedulerXenonHost(schedulerConfig.getXenonConfig(),
            hostClientFactory, schedulerConfig, checker, cloudStoreHelper);
    logger.info("Created Scheduler Xenon Host");

    logger.info("Starting Scheduler Xenon Host");
    schedulerXenonHost.start();
    logger.info("Started Scheduler Xenon Host");


    String schedulerXenonAddress = schedulerConfig.getXenonConfig().getRegistrationAddress();
    Integer schedulerXenonPort = schedulerConfig.getXenonConfig().getPort();
    logger.info("Registering Scheduler Xenon Host with Zookeeper at {}:{}", schedulerXenonAddress, schedulerXenonPort);
    registerServiceWithZookeeper(Constants.SCHEDULER_SERVICE_NAME, zkModule, zkClient, schedulerXenonAddress,
        schedulerXenonPort);
    logger.info("Registered Scheduler Xenon Host with Zookeeper");

    return schedulerXenonHost;
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
}
