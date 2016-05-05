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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Root scheduler entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMilliSeconds = TimeUnit.SECONDS.toMillis(30);

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("Root scheduler")
        .defaultHelp(true)
        .description("Photon Controller RootScheduler");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    Config config = getConfig(namespace);

    new LoggingFactory(config.getLogging(), "rootscheduler").configure();

    Injector injector = Guice.createInjector(
        new RootSchedulerModule(),
        new ZookeeperModule(config.getZookeeper()),
        new ThriftModule(),
        new ThriftServiceModule<>(new TypeLiteral<Host.AsyncClient>() {
        }));

    ZookeeperServerSetFactory serverSetFactory = injector.getInstance(ZookeeperServerSetFactory.class);
    HostClientFactory hostClientFactory = injector.getInstance(HostClientFactory.class);
    ServerSet cloudStoreServerSet = serverSetFactory.createServiceServerSet("cloudstore", true);

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    final SchedulerXenonHost host = new SchedulerXenonHost(config.getXenonConfig(), hostClientFactory,
        config, checker, cloudStoreHelper);

    final ServiceNodeFactory serviceNodeFactory = injector.getInstance(ServiceNodeFactory.class);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        host.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    // Start the Xenon Host.
    host.start();

    // Initialize the Zookeeper ServerSet.
    ServerSet schedulerServerSet = serverSetFactory.createServiceServerSet("root-scheduler", true);
    logger.info("RootSchedulerServerSet {}", schedulerServerSet.getServers());

    // Register the local Scheduler Node with Zookeeper.
    registerWithZookeeper(serviceNodeFactory,
        config.getXenonConfig().getRegistrationAddress(),
        config.getXenonConfig().getPort());
  }

  private static Config getConfig(Namespace namespace) {
    Config config = null;
    try {
      config = ConfigBuilder.build(Config.class, namespace.getString("file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }

  private static void registerWithZookeeper(ServiceNodeFactory serviceNodeFactory,
                                            String registrationIpAddress,
                                            int port) {
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress, port);
    ServiceNode serviceNode = serviceNodeFactory.createSimple("root-scheduler", registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMilliSeconds);
  }
}
