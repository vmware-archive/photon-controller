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

package com.vmware.photon.controller.rootscheduler.simulator;

import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.psim.gen.InitializeRequest;
import com.vmware.photon.controller.psim.gen.PlacementSimulator;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * A stub test case for {@link CloudStoreLoader}.
 */
public class PlacementSimulatorTest {
  private static final Logger logger = LoggerFactory.getLogger(PlacementSimulatorTest.class);

  private TestEnvironment cloudStore;

  private Process pythonServer;

  private final int pythonServerPort = 12345;

  TTransport transport;

  PlacementSimulator.Client client;

  @BeforeClass
  public void setUpClass() throws Throwable {
    cloudStore = TestEnvironment.create(1);

    // There are 10 datastores.
    // TODO(mmutsuzaki) Allow the user to define datastore sizes.
    int numDatastores = 10;
    CloudStoreLoader.loadDatastores(cloudStore, numDatastores);

    int numHosts = 100;
    // 50% of the hosts have 4 CPUs and 8 GB of memory.
    // 30% of the hosts have 8 CPUs and 16 GB of memory.
    // 20% of the hosts have 16 CPUs and 32 GB of memory.
    Map<CloudStoreLoader.HostConfiguration, Double> hostConfigurations = ImmutableMap.of(
        new CloudStoreLoader.HostConfiguration(4, 8 * 1024), 0.5,
        new CloudStoreLoader.HostConfiguration(8, 16 * 1024), 0.3,
        new CloudStoreLoader.HostConfiguration(16, 32 * 1024), 0.2);

    // Each host has 4 datastores.
    IntegerDistribution datastoreDistribution = new UniformIntegerDistribution(4, 4);
    CloudStoreLoader.loadHosts(cloudStore, numHosts, hostConfigurations,
        numDatastores, datastoreDistribution);

    // Start the python server
    int port = 12345;
    File logDir = new File(Paths.get(System.getProperty("java.io.tmpdir"), "photon-controller").toString());
    logDir.mkdirs();
    pythonServer = CloudStoreLoader.startPythonServer(port, logDir);

    // Create the thrift client
    TSocket socket = new TSocket("localhost", pythonServerPort);
    transport = new TFramedTransport(socket);
    for (int i = 0; i < 10; i++) {
      try {
        transport.open();
        break;
      } catch (Exception ex) {
        logger.info("retrying...", ex);
        Thread.sleep(1000);
      }
    }
    TCompactProtocol protocol = new TCompactProtocol(transport);
    TMultiplexedProtocol proto = new TMultiplexedProtocol(protocol, "psim");
    client = new PlacementSimulator.Client(proto);
  }

  @AfterClass
  public void tearDownClass() throws Throwable {
    transport.close();
    pythonServer.destroy();
    cloudStore.stop();
  }

  @Test
  public void testStub() throws Throwable {
    InetSocketAddress address = cloudStore.getServerSet().getServers().iterator().next();
    ServerAddress serverAddress = new ServerAddress(address.getHostString(), address.getPort());
    InitializeRequest request = new InitializeRequest(serverAddress);
    logger.info("request {}, response {}", request, client.initialize(request));
  }
}
