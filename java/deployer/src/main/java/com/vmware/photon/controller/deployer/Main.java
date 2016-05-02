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

package com.vmware.photon.controller.deployer;


import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.logging.LoggingFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.host.gen.Host;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the entry point for the deployer service.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /**
   * This method provides the main entry point for the deployer service.
   *
   * @param args
   * @throws Throwable
   */
  public static void main(String[] args) throws Throwable {

    try {

      LoggingFactory.bootstrap();

      ArgumentParser parser = ArgumentParsers.newArgumentParser("Deployer")
          .defaultHelp(true)
          .description("Photon Controller Deployer");
      parser.addArgument("file").help("configuration file");

      logger.info("Parsing command-line arguments");
      Namespace namespace = parser.parseArgsOrFail(args);

      logger.info("Parsing service configuration file");
      DeployerConfig deployerConfig = ConfigBuilder.build(DeployerConfig.class, namespace.getString("file"));

      logger.info("Configuring logging");
      new LoggingFactory(deployerConfig.getLogging(), "deployer").configure();

      logger.info("Creating Guice injector");
      Injector injector = Guice.createInjector(
          new DeployerModule(deployerConfig),
          new ZookeeperModule(deployerConfig.getZookeeper()),
          new ThriftModule(),
          new ThriftServiceModule<>(
              new TypeLiteral<AgentControl.AsyncClient>() {
              }
          ),
          new ThriftServiceModule<>(
              new TypeLiteral<Host.AsyncClient>() {
              }
          ));

      logger.info("Creating Thrift server instance");
      final DeployerServer thriftServer = injector.getInstance(DeployerServer.class);

      logger.info("Creating DCP host instance");
      final DeployerXenonServiceHost deployerDcpServiceHost = injector.getInstance(DeployerXenonServiceHost.class);

      logger.info("Adding shutdown hook");
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          logger.info("Shutting down");
          thriftServer.stop();
          deployerDcpServiceHost.stop();
          logger.info("Done");
          LoggingFactory.detachAndStop();
        }
      });

      logger.info("Starting DCP host service");
      deployerDcpServiceHost.start();

      logger.info("Starting Thrift service");
      thriftServer.serve();

    } catch (Throwable t) {
      t.printStackTrace();
      logger.error(t.getMessage());
      System.exit(1);
    }
  }
}
