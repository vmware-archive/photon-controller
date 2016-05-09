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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Cloud-store entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMsec = TimeUnit.SECONDS.toMillis(30);

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("CloudStore")
        .defaultHelp(true)
        .description("Photon Controller CloudStore");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    CloudStoreConfig cloudStoreConfig = getConfig(namespace);

    new LoggingFactory(cloudStoreConfig.getLogging(), "cloudstore").configure();

    Injector injector = Guice.createInjector(
        new CloudStoreModule(cloudStoreConfig),
        new ZookeeperModule(cloudStoreConfig.getZookeeper()),
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

    final CloudStoreXenonHost cloudStoreDcpHost = injector.getInstance(CloudStoreXenonHost.class);
    final ServiceNodeFactory serviceNodeFactory = injector.getInstance(ServiceNodeFactory.class);



    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        cloudStoreDcpHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    cloudStoreDcpHost.start();

    // initialize CloudStoreServerSet instance
    ServerSet cloudStoreServerSet = injector.getInstance(Key.get(ServerSet.class, CloudStoreServerSet.class));
    logger.info("CloudStoreServerSet {}", cloudStoreServerSet.getServers());

    registerWithZookeeper(serviceNodeFactory,
        cloudStoreConfig.getXenonConfig().getRegistrationAddress(),
        cloudStoreConfig.getXenonConfig().getPort());
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

  private static void registerWithZookeeper(ServiceNodeFactory serviceNodeFactory, String registrationIpAddress,
                                            int port) {
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress, port);
    ServiceNode serviceNode = serviceNodeFactory.createSimple("cloudstore", registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec);
  }
}
