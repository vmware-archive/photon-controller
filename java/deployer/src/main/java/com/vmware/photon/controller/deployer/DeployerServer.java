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

import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.chairman.hierarchy.HierarchyManager;
import com.vmware.photon.controller.chairman.service.ChairmanService;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ThriftEventHandler;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.deployer.service.DeployerService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
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
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * This class initializes the Thrift server for the deployer service and responds to Thrift calls.
 */
public class DeployerServer {
  public static final String CHAIRMAN_SERVICE_NAME = "Chairman";
  public static final String SERVICE_NAME = "Deployer";

  private static final Logger logger = LoggerFactory.getLogger(DeployerServer.class);
  private static final long retryIntervalMsec = TimeUnit.SECONDS.toMillis(5);

  private final ServiceNodeFactory serviceNodeFactory;
  private final TTransportFactory transportFactory;
  private final TProtocolFactory protocolFactory;
  private final ThriftFactory thriftFactory;
  private final DeployerService deployerService;
  private final ChairmanService chairmanService;
  private final BuildInfo buildInfo;
  private final String bind;
  private final String registrationAddress;
  private final int port;
  private final CloseableHttpAsyncClient httpClient;
  private final HierarchyManager hierarchyManager;

  private TServer server;
  private ServiceNode serviceNode;

  @Inject
  public DeployerServer(ServiceNodeFactory serviceNodeFactory,
                        TProtocolFactory protocolFactory,
                        TTransportFactory transportFactory,
                        ThriftFactory thriftFactory,
                        ChairmanService chairmanService,
                        DeployerService deployerService,
                        BuildInfo buildInfo,
                        @DeployerConfig.Bind String bind,
                        @DeployerConfig.RegistrationAddress String registrationAddress,
                        @DeployerConfig.Port int port,
                        CloseableHttpAsyncClient httpClient,
                        HierarchyManager hierarchyManager) {
    this.serviceNodeFactory = serviceNodeFactory;
    this.transportFactory = transportFactory;
    this.protocolFactory = protocolFactory;
    this.thriftFactory = thriftFactory;
    this.chairmanService = chairmanService;
    this.deployerService = deployerService;
    this.buildInfo = buildInfo;
    this.bind = bind;
    this.registrationAddress = registrationAddress;
    this.port = port;
    this.httpClient = httpClient;
    this.hierarchyManager = hierarchyManager;
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

    Chairman.Processor<ChairmanService> chairmanProcessor = new Chairman.Processor<>(chairmanService);
    Deployer.Processor<DeployerService> deployerProcessor = new Deployer.Processor<>(deployerService);
    TMultiplexedProcessor processor = new TMultiplexedProcessor();
    processor.registerProcessor(CHAIRMAN_SERVICE_NAME, chairmanProcessor);
    processor.registerProcessor(SERVICE_NAME, deployerProcessor);

    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory)
    );

    // Need to re-fetch local port in case it was 0
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress,
        transport.getServerSocket().getLocalPort());
    serviceNode = serviceNodeFactory.createSimple("deployer", registrationSocketAddress);

    server.setServerEventHandler(getThriftEventHandler());

    // Set up leader election for hierarchy manager
    serviceNode = serviceNodeFactory.createLeader("tree-builder", registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec, hierarchyManager);

    logger.info("Starting deployer ({})", buildInfo);
    logger.info("Listening on: {}", bindSocketAddress);
    logger.info("Registering address: {}", registrationSocketAddress);
    logger.info("HttpClient is: {}", httpClient);
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

    if (httpClient != null) {
      logger.info("Closing httpClient");
      try {
        httpClient.close();
      } catch (IOException e) {
        logger.error("Closing httpClient resulted in an error.", e);
      }
    }
  }

  @VisibleForTesting
  protected ThriftEventHandler getThriftEventHandler() {
    return thriftFactory.create(deployerService, serviceNode);
  }
}
