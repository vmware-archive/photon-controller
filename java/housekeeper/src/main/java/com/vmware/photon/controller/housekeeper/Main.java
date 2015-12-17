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

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Housekeeper entry point.
 */
public class Main {

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
        new HousekeeperModule(config),
        new ZookeeperModule(config.getZookeeper()),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        )
    );

    final HousekeeperServer thriftServer = injector.getInstance(HousekeeperServer.class);
    final HousekeeperDcpServiceHost housekeeperDcpServiceHost = injector.getInstance(HousekeeperDcpServiceHost.class);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down");
        thriftServer.stop();
        housekeeperDcpServiceHost.stop();
        logger.info("Done");
        LoggingFactory.detachAndStop();
      }
    });

    housekeeperDcpServiceHost.start();
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
