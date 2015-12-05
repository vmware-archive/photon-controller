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

package com.vmware.photon.controller.chairman;

import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;

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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * ChairmanServer sets up and runs Chairman Thrift server.
 */
public class ChairmanServer {
  private static final Logger logger = LoggerFactory.getLogger(ChairmanServer.class);
  private static final long retryIntervalMsec = TimeUnit.SECONDS.toMillis(5);

  private final ServiceNodeFactory serviceNodeFactory;
  private final TTransportFactory transportFactory;
  private final TProtocolFactory protocolFactory;
  private final ThriftFactory thriftFactory;
  private final ChairmanService chairmanService;
  private final BuildInfo buildInfo;
  private final String bind;
  private final String registrationAddress;
  private final int port;
  private TServer server;
  private ServiceNode serviceNode;

  @Inject
  public ChairmanServer(ServiceNodeFactory serviceNodeFactory,
                        TProtocolFactory protocolFactory,
                        TTransportFactory transportFactory,
                        ThriftFactory thriftFactory,
                        ChairmanService chairmanService,
                        BuildInfo buildInfo,
                        @Config.Bind String bind,
                        @Config.RegistrationAddress String registrationAddress,
                        @Config.Port int port) {
    this.serviceNodeFactory = serviceNodeFactory;
    this.transportFactory = transportFactory;
    this.protocolFactory = protocolFactory;
    this.thriftFactory = thriftFactory;
    this.chairmanService = chairmanService;
    this.buildInfo = buildInfo;
    this.bind = bind;
    this.registrationAddress = registrationAddress;
    this.port = port;
  }

  public void serve() throws TTransportException, IOException {
    InetAddress registrationIpAddress = InetAddress.getByName(registrationAddress);
    if (registrationIpAddress.isAnyLocalAddress()) {
      logger.error("Using a wildcard registration address will not work with service registry: {}",
          registrationAddress);
      throw new IllegalArgumentException("Wildcard registration address");
    }

    InetAddress bindIpAddress = InetAddress.getByName(bind);
    InetSocketAddress bindSocketAddress = new InetSocketAddress(bindIpAddress, port);
    TServerSocket transport = new TServerSocket(bindSocketAddress);

    Chairman.Processor<ChairmanService> chairmanProcessor = new Chairman.Processor<>(chairmanService);
    TMultiplexedProcessor processor = new TMultiplexedProcessor();
    processor.registerProcessor("Chairman", chairmanProcessor);

    // TODO(vspivak): add configurable executor
    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory));

    // Need to re-fetch local port in case it was 0
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress,
        transport.getServerSocket().getLocalPort());
    serviceNode = serviceNodeFactory.createLeader("chairman", registrationSocketAddress);

    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec);

    logger.info("Starting chairman ({})", buildInfo);
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
      logger.debug("Left service");
    }
  }
}
