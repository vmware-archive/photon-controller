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
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.photon.controller.scheduler.gen.Scheduler;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PhotonControllerCore entry point.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final long retryIntervalMsec = TimeUnit.SECONDS.toMillis(30);

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
        ),
        new ThriftServiceModule<>(
            new TypeLiteral<Scheduler.AsyncClient>() {
            }
        )
    );

    List<ServiceHost> serviceHosts = new ArrayList<>();
    ServiceHost cloudStoreHost = startCloudStore(cloudStoreConfig, injector);
    serviceHosts.add(cloudStoreHost);
    ServiceHost schedulerHost = startScheduler(schedulerConfig, injector);
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

  private static ServiceHost startCloudStore(CloudStoreConfig cloudStoreConfig, Injector injector) throws Throwable {
    final ServiceNodeFactory serviceNodeFactory = injector.getInstance(ServiceNodeFactory.class);
    final CloudStoreXenonHost cloudStoreDcpHost = injector.getInstance(CloudStoreXenonHost.class);

    cloudStoreDcpHost.start();

    registerServiceWithZookeeper("cloudstore", serviceNodeFactory,
            cloudStoreConfig.getXenonConfig().getRegistrationAddress(),
            cloudStoreConfig.getXenonConfig().getPort());

    return cloudStoreDcpHost;
  }

  private static ServiceHost startScheduler(RootSchedulerConfig schedulerConfig, Injector injector) throws Throwable {
    final ServiceNodeFactory serviceNodeFactory = injector.getInstance(ServiceNodeFactory.class);
    ZookeeperServerSetFactory serverSetFactory = injector.getInstance(ZookeeperServerSetFactory.class);
    HostClientFactory hostClientFactory = injector.getInstance(HostClientFactory.class);
    ServerSet cloudStoreServerSet = serverSetFactory.createServiceServerSet("cloudstore", true);

    final CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    final ConstraintChecker checker = new CloudStoreConstraintChecker(cloudStoreHelper);

    final SchedulerXenonHost schedulerXenonHost = new SchedulerXenonHost(schedulerConfig.getXenonConfig(),
            hostClientFactory, schedulerConfig, checker, cloudStoreHelper);

    schedulerXenonHost.start();

    registerServiceWithZookeeper("root-scheduler", serviceNodeFactory,
            schedulerConfig.getXenonConfig().getRegistrationAddress(),
            schedulerConfig.getXenonConfig().getPort());

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

  private static void registerServiceWithZookeeper(String serviceName, ServiceNodeFactory serviceNodeFactory,
                                                      String registrationIpAddress, int port) {
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress, port);
    ServiceNode serviceNode = serviceNodeFactory.createSimple(serviceName, registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec);
  }
}
