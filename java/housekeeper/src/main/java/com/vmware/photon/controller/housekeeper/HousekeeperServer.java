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

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.thrift.ThriftEventHandler;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;

import com.google.common.annotations.VisibleForTesting;
import org.apache.curator.framework.CuratorFramework;
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
 * HousekeeperServer sets up and runs Housekeeper Thrift server.
 */
public class HousekeeperServer {
  public static final String SERVICE_NAME = "Housekeeper";
  private static final Logger logger = LoggerFactory.getLogger(HousekeeperServer.class);
  private final TTransportFactory transportFactory;
  private final TProtocolFactory protocolFactory;
  private final ThriftFactory thriftFactory;
  private final HousekeeperService housekeeperService;
  private final String bind;
  private final String registrationAddress;
  private final int port;
  private final ZookeeperModule zkModule;
  private final CuratorFramework zkClient;

  private TServer server;
  private ServiceNode serviceNode;

  public HousekeeperServer(ZookeeperModule zkModule,
                           CuratorFramework zkClient,
                           TProtocolFactory protocolFactory,
                           TTransportFactory transportFactory,
                           ThriftFactory thriftFactory,
                           HousekeeperService housekeeperService,
                           ThriftConfig thriftConfig) {
    this.zkModule = zkModule;
    this.zkClient = zkClient;
    this.transportFactory = transportFactory;
    this.protocolFactory = protocolFactory;
    this.thriftFactory = thriftFactory;
    this.housekeeperService = housekeeperService;
    this.bind = thriftConfig.getBindAddress();
    this.registrationAddress = thriftConfig.getRegistrationAddress();
    this.port = thriftConfig.getPort();
  }

  public void serve() throws UnknownHostException, TTransportException {
    InetAddress registrationIpAddress = InetAddress.getByName(registrationAddress);
    if (registrationIpAddress.isAnyLocalAddress()) {
      logger.error("Using a wildcard registration address will not work with service registry: {}",
          registrationAddress);
      throw new IllegalArgumentException("Wildcard registration address");
    }

    InetAddress bindIpAddress = InetAddress.getByName(bind);
    InetSocketAddress bindSocketAddress = new InetSocketAddress(bindIpAddress, port);
    TServerSocket transport = new TServerSocket(bindSocketAddress);

    Housekeeper.Processor<HousekeeperService> housekeeperProcessor = new Housekeeper.Processor<>(housekeeperService);
    TMultiplexedProcessor processor = new TMultiplexedProcessor();
    processor.registerProcessor(SERVICE_NAME, housekeeperProcessor);

    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory)
    );

    // Need to re-fetch local port in case it was 0
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress,
        transport.getServerSocket().getLocalPort());
    serviceNode = zkModule.getSimpleServiceNode(zkClient, Constants.HOUSEKEEPER_SERVICE_NAME,
        registrationSocketAddress);

    server.setServerEventHandler(getThriftEventHandler());

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
      logger.info("Leaving service");
      serviceNode.leave();
    }
  }

  @VisibleForTesting
  protected ThriftEventHandler getThriftEventHandler() {
    return thriftFactory.create(housekeeperService, serviceNode);
  }
}
