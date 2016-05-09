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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.cloudstore.CloudStoreConfig;
import com.vmware.photon.controller.cloudstore.CloudStoreModule;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PhotonControllerCore entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMillis = TimeUnit.SECONDS.toMillis(30);
  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";
  public static final String SCHEDULER_SERVICE_NAME = "root-scheduler";

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("PhotonControllerCore")
        .defaultHelp(true)
        .description("Photon Controller Core");
    parser.addArgument("cloud-store-file").help("cloud store configuration file");
    parser.addArgument("root-scheduler-file").help("root scheduler configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    CloudStoreConfig cloudStoreConfig = getCloudStoreConfig(namespace);
    RootSchedulerConfig schedulerConfig = getRootSchedulerConfig(namespace);

    // TODO(bmace) -- for now pull the logging config from cloud store
    new LoggingFactory(cloudStoreConfig.getLogging(), "photon-controller-core").configure();

    Injector injector = Guice.createInjector(
        new CloudStoreModule(),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new ThriftServiceModule<>(
            new TypeLiteral<AgentControl.AsyncClient>() {
            }
        )
    );

    final ZookeeperModule zkModule = new ZookeeperModule(cloudStoreConfig.getZookeeper());
    final CuratorFramework zkClient = zkModule.getCuratorFramework();

    List<ServiceHost> serviceHosts = new ArrayList<>();
    ServiceHost cloudStoreHost = startCloudStore(cloudStoreConfig, zkModule, zkClient, injector);
    serviceHosts.add(cloudStoreHost);
    ServiceHost schedulerHost = startScheduler(schedulerConfig, zkModule, zkClient, injector);
    serviceHosts.add(schedulerHost);

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
                                             CuratorFramework zkClient, Injector injector) throws Throwable {
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);
    final HostClientFactory hostClientFactory = injector.getInstance(HostClientFactory.class);
    final AgentControlClientFactory agentControlClientFactory = injector.getInstance(AgentControlClientFactory.class);

    logger.info("Creating CloudStore");
    final CloudStoreXenonHost cloudStoreXenonHost = new CloudStoreXenonHost(cloudStoreConfig.getXenonConfig(),
            hostClientFactory, agentControlClientFactory, serviceConfigFactory);
    logger.info("Created CloudStore");

    logger.info("Starting CloudStore");
    cloudStoreXenonHost.start();
    logger.info("Started CloudStore");

    logger.info("Registering CloudStore with Zookeeper");
    registerServiceWithZookeeper(CLOUDSTORE_SERVICE_NAME, zkModule, zkClient,
            cloudStoreConfig.getXenonConfig().getRegistrationAddress(),
            cloudStoreConfig.getXenonConfig().getPort());
    logger.info("Registered CloudStore with Zookeeper");

    return cloudStoreXenonHost;
  }

  private static ServiceHost startScheduler(RootSchedulerConfig schedulerConfig, ZookeeperModule zkModule,
                                            CuratorFramework zkClient, Injector injector) throws Throwable {
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, CLOUDSTORE_SERVICE_NAME, true);
    HostClientFactory hostClientFactory = injector.getInstance(HostClientFactory.class);

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    logger.info("Creating Scheduler");
    final SchedulerXenonHost schedulerXenonHost = new SchedulerXenonHost(schedulerConfig.getXenonConfig(),
            hostClientFactory, schedulerConfig, checker, cloudStoreHelper);
    logger.info("Created Scheduler");

    logger.info("Starting Scheduler");
    schedulerXenonHost.start();
    logger.info("Started Scheduler");


    logger.info("Registering Scheduler with Zookeeper");
    registerServiceWithZookeeper(SCHEDULER_SERVICE_NAME, zkModule, zkClient,
            schedulerConfig.getXenonConfig().getRegistrationAddress(),
            schedulerConfig.getXenonConfig().getPort());
    logger.info("Registered Scheduler with Zookeeper");

    return schedulerXenonHost;
  }

  private static CloudStoreConfig getCloudStoreConfig(Namespace namespace) {
    CloudStoreConfig config = null;
    try {
      config = ConfigBuilder.build(CloudStoreConfig.class, namespace.getString("cloud-store-file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }

  private static RootSchedulerConfig getRootSchedulerConfig(Namespace namespace) {
    RootSchedulerConfig config = null;
    try {
      config = ConfigBuilder.build(RootSchedulerConfig.class, namespace.getString("root-scheduler-file"));
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
