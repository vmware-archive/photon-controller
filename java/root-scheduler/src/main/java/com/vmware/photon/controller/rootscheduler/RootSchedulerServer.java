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

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;

import com.google.inject.Inject;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Root scheduler server.
 */
public class RootSchedulerServer {
  private static final Logger logger = LoggerFactory.getLogger(RootSchedulerServer.class);

  private final String bind;
  private final String registrationAddress;
  private final int port;
  private final RootScheduler.Iface rootSchedulerService;
  private final TProtocolFactory protocolFactory;
  private final TTransportFactory transportFactory;
  private final ServiceNodeFactory serviceNodeFactory;
  private final BuildInfo buildInfo;
  private final ThriftFactory thriftFactory;

  private volatile TServer server;
  private volatile ServiceNode serviceNode;

  @Inject
  public RootSchedulerServer(ThriftConfig thriftConfig,
                             RootScheduler.Iface rootSchedulerService,
                             TProtocolFactory protocolFactory,
                             TTransportFactory transportFactory,
                             ServiceNodeFactory serviceNodeFactory,
                             BuildInfo buildInfo,
                             ThriftFactory thriftFactory) {
    this.bind = thriftConfig.getBindAddress();
    this.registrationAddress = thriftConfig.getRegistrationAddress();
    this.port = thriftConfig.getPort();
    this.rootSchedulerService = rootSchedulerService;
    this.protocolFactory = protocolFactory;
    this.transportFactory = transportFactory;
    this.serviceNodeFactory = serviceNodeFactory;
    this.buildInfo = buildInfo;
    this.thriftFactory = thriftFactory;
  }

  public void serve() throws TTransportException, UnknownHostException {
    InetAddress registrationIpAddress = InetAddress.getByName(registrationAddress);
    if (registrationIpAddress.isAnyLocalAddress()) {
      logger.error("Using a wildcard registration address will not work with service registry: {}",
          registrationAddress);
      throw new IllegalArgumentException("Wildcard registration address");
    }

    InetAddress bindIpAddress = InetAddress.getByName(bind);
    InetSocketAddress bindSocketAddress = new InetSocketAddress(bindIpAddress, port);
    TServerSocket transport = new TServerSocket(bindSocketAddress);

    RootScheduler.Processor<RootScheduler.Iface> rootSchedulerProcessor =
        new RootScheduler.Processor<>(rootSchedulerService);
    TMultiplexedProcessor processor = new TMultiplexedProcessor();
    processor.registerProcessor("RootScheduler", rootSchedulerProcessor);

    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory)
    );

    // Need to re-fetch local port in case it was 0
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress,
        transport.getServerSocket().getLocalPort());
    serviceNode = serviceNodeFactory.createSimple("root-scheduler", registrationSocketAddress);
    if (rootSchedulerService instanceof ServiceNodeEventHandler) {
      server.setServerEventHandler(thriftFactory.create((ServiceNodeEventHandler) rootSchedulerService, serviceNode));
    }
    logger.info("Starting root scheduler ({})", buildInfo);
    logger.info("Listening on: {}", bindSocketAddress);
    logger.info("Registering address: {}", registrationSocketAddress);
    server.serve();
  }

  public void stop() {
    if (server != null) {
      logger.info("Stopping server");
      server.stop();
    }

    if (serviceNode != null) {
      logger.info("Leaving root scheduler service");
      serviceNode.leave();
    }
  }
}
