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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Cloud-store entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMillisec = TimeUnit.SECONDS.toMillis(30);

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("CloudStore")
        .defaultHelp(true)
        .description("Photon Controller CloudStore");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    CloudStoreConfig cloudStoreConfig = getConfig(namespace);

    new LoggingFactory(cloudStoreConfig.getLogging(), "cloudstore").configure();

    ZookeeperModule zkModule = new ZookeeperModule(cloudStoreConfig.getZookeeper());
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);

    ThriftModule thriftModule = new ThriftModule();
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final AgentControlClientFactory agentControlClientFactory = thriftModule.getAgentControlClientFactory();

    final CloudStoreXenonHost cloudStoreXenonHost = new CloudStoreXenonHost(cloudStoreConfig.getXenonConfig(),
        hostClientFactory, agentControlClientFactory, serviceConfigFactory);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        cloudStoreXenonHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    // Start the CloudStore Xenon Host.
    cloudStoreXenonHost.start();

    zkModule.registerWithZookeeper(zkClient, Constants.CLOUDSTORE_SERVICE_NAME,
        cloudStoreConfig.getXenonConfig().getRegistrationAddress(),
        cloudStoreConfig.getXenonConfig().getPort(), retryIntervalMillisec);
  }

  private static CloudStoreConfig getConfig(Namespace namespace) {
    CloudStoreConfig config = null;
    try {
      config = ConfigBuilder.build(CloudStoreConfig.class, namespace.getString("file"));
    } catch (BadConfigException e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    return config;
  }
}
