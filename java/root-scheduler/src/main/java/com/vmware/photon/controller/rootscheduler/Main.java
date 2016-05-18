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

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    RootSchedulerConfig config = getConfig(namespace);

    new LoggingFactory(config.getLogging(), "rootscheduler").configure();

    ZookeeperModule zkModule = new ZookeeperModule(config.getZookeeper());
    // Singleton
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, Constants.CLOUDSTORE_SERVICE_NAME, true);

    ThriftModule thriftModule = new ThriftModule();
    HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    final SchedulerXenonHost host = new SchedulerXenonHost(config.getXenonConfig(), hostClientFactory,
        config, checker, cloudStoreHelper);

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

    // Register the local Scheduler Node with Zookeeper.
    zkModule.registerWithZookeeper(zkClient, Constants.SCHEDULER_SERVICE_NAME,
        config.getXenonConfig().getRegistrationAddress(),
        config.getXenonConfig().getPort(), retryIntervalMilliSeconds);
  }

  private static RootSchedulerConfig getConfig(Namespace namespace) {
    RootSchedulerConfig config = null;
    try {
      config = ConfigBuilder.build(RootSchedulerConfig.class, namespace.getString("file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }
}
