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

package com.vmware.photon.controller.housekeeper;

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
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

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

/**
 * Housekeeper entry point.
 */
public class Main {
  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";
  public static final String HOUSEKEEPER_SERVICE_NAME = "housekeeper";

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Throwable {
    LoggingFactory.bootstrap();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("Housekeeper")
        .defaultHelp(true)
        .description("Photon Controller Housekeeper");
    parser.addArgument("file").help("configuration file");

    Namespace namespace = parser.parseArgsOrFail(args);

    Config config = getConfig(namespace);

    new LoggingFactory(config.getLogging(), "housekeeper").configure();

    Injector injector = Guice.createInjector(
        new HousekeeperModule(),
        new ThriftModule()
    );

    final ZookeeperModule zkModule = new ZookeeperModule(config.getZookeeper());
    final CuratorFramework zkClient = zkModule.getCuratorFramework();
    final ServiceConfigFactory serviceConfigFactory = zkModule.getServiceConfigFactory(zkClient);
    ServerSet cloudStoreServerSet = zkModule.getZookeeperServerSet(zkClient, CLOUDSTORE_SERVICE_NAME, true);

    final ThriftModule thriftModule = injector.getInstance(ThriftModule.class);
    final HostClientFactory hostClientFactory = thriftModule.getHostClientFactory();
    final TProtocolFactory tProtocolFactory = injector.getInstance(TProtocolFactory.class);
    final TTransportFactory tTransportFactory = injector.getInstance(TTransportFactory.class);
    final ThriftFactory thriftFactory = injector.getInstance(ThriftFactory.class);

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final NsxClientFactory nsxClientFactory = new NsxClientFactory();

    final HousekeeperXenonServiceHost housekeeperXenonServiceHost = new HousekeeperXenonServiceHost(
        config.getXenonConfig(), cloudStoreHelper, hostClientFactory, serviceConfigFactory, nsxClientFactory);

    ServerSet housekeeperServerSet = zkModule.getZookeeperServerSet(zkClient, HOUSEKEEPER_SERVICE_NAME, true);
    final HousekeeperService housekeeperService = new HousekeeperService(housekeeperServerSet,
        housekeeperXenonServiceHost);

    final HousekeeperServer thriftServer = new HousekeeperServer(zkModule, zkClient, tProtocolFactory,
        tTransportFactory, thriftFactory, housekeeperService, config.getThriftConfig());

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        thriftServer.stop();
        housekeeperXenonServiceHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    housekeeperXenonServiceHost.start();
    thriftServer.serve();
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

}
