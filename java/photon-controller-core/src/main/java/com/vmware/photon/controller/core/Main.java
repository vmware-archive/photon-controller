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
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.housekeeper.HousekeeperConfig;
import com.vmware.photon.controller.housekeeper.xenon.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerServiceGroup;
import com.vmware.xenon.common.ServiceHost;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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
    HousekeeperConfig housekeeperConfig = photonControllerConfig.getHousekeeperConfig();


    new LoggingFactory(photonControllerConfig.getLogging(), "photon-controller-core").configure();

    // the zk config info is currently taken from the cloud store config but this is temporary as
    // zookeeper usage is going away so this will all be removed.
    final ZookeeperModule zkModule = new ZookeeperModule(cloudStoreConfig.getZookeeper());
    final ThriftModule thriftModule = new ThriftModule();

    ServiceHost xenonHost = startXenonHost(photonControllerConfig, zkModule, thriftModule);

    // Housekeeper holds on to the thread on start (for thrift) so start it last
    ServiceHost housekeeperHost = startHousekeeper(housekeeperConfig, zkModule, thriftModule);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        housekeeperHost.stop();
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
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();

    // Values for Scheduler
    final ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient,
            Constants.CLOUDSTORE_SERVICE_NAME, true);
    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    logger.info("Creating PhotonController Xenon Host");
    final PhotonControllerXenonHost photonControllerXenonHost =
            new PhotonControllerXenonHost(photonControllerConfig.getCloudStoreConfig().getXenonConfig(),
                    hostClientFactory, agentControlClientFactory, serviceConfigFactory, cloudStoreHelper);
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

    logger.info("Starting PhotonController Xenon Host");
    photonControllerXenonHost.start();
    logger.info("Started PhotonController Xenon Host");

    // For now we register both cloud store and scheduler services with zookeeper so that users of
    // the services don't need to change their lookup / usage behavior but now we use the same
    // address / port for both (for now the cloudstore address / port)
    String cloudStoreXenonAddress =
            photonControllerConfig.getCloudStoreConfig().getXenonConfig().getRegistrationAddress();
    Integer cloudStoreXenonPort = photonControllerConfig.getCloudStoreConfig().getXenonConfig().getPort();
    logger.info("Registering CloudStore Xenon Host with Zookeeper at {}:{}",
            cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.CLOUDSTORE_SERVICE_NAME, zkModule, zkClient,
            cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered CloudStore Xenon Host with Zookeeper");

    logger.info("Registering Scheduler Xenon Host with Zookeeper at {}:{}",
            cloudStoreXenonAddress, cloudStoreXenonPort);
    registerServiceWithZookeeper(Constants.SCHEDULER_SERVICE_NAME, zkModule, zkClient,
            cloudStoreXenonAddress, cloudStoreXenonPort);
    logger.info("Registered Scheduler Xenon Host with Zookeeper");

    return photonControllerXenonHost;
  }

  private static ServiceHost startHousekeeper(HousekeeperConfig housekeeperConfig, ZookeeperModule zkModule,
                                              ThriftModule thriftModule) throws Throwable {
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.CLOUDSTORE_SERVICE_NAME, true);
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);

    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    logger.info("Creating Housekeeper Xenon Host");
    final HousekeeperXenonServiceHost housekeeperXenonHost = new HousekeeperXenonServiceHost(
            housekeeperConfig.getXenonConfig(), cloudStoreHelper, hostClientFactory, serviceConfigFactory,
            nsxClientFactory);
    logger.info("Created Housekeeper Xenon Host");

    String housekeeperXenonAddress = housekeeperConfig.getXenonConfig().getRegistrationAddress();
    Integer housekeeperXenonPort = housekeeperConfig.getXenonConfig().getPort();

    logger.info("Registering Housekeeper Xenon Host with Zookeeper at {}:{}",
        housekeeperXenonAddress, housekeeperXenonPort);
    registerServiceWithZookeeper(Constants.HOUSEKEEPER_SERVICE_NAME, zkModule, zkClient,
        housekeeperXenonAddress, housekeeperXenonPort);
    logger.info("Registered Housekeeper Xenon Host with Zookeeper");

    logger.info("Starting Housekeeper Xenon Host");
    housekeeperXenonHost.start();
    logger.info("Started Housekeeper Xenon Host");

    return housekeeperXenonHost;
  }

  private static CloudStoreServiceGroup createCloudStoreServiceGroup() throws Throwable {
    return new CloudStoreServiceGroup();
  }

  private static SchedulerServiceGroup createSchedulerServiceGroup(RootSchedulerConfig rootSchedulerConfig,
          ConstraintChecker constraintChecker) throws Throwable {
    return  new SchedulerServiceGroup(rootSchedulerConfig.getRoot(), constraintChecker);
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
