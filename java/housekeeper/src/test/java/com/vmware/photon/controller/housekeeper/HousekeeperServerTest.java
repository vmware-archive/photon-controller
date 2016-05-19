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

import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftEventHandler;
import com.vmware.photon.controller.common.thrift.ThriftFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.inject.Injector;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransportFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Test {@link HousekeeperServer}.
 */
public class HousekeeperServerTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for host service startup.
   */
  public class StartupTest {

    private Injector injector;
    private Config config;
    private HousekeeperServer server;
    private ServiceNodeFactory serviceNodeFactory;
    private TProtocolFactory tProtocolFactory;
    private TTransportFactory tTransportFactory;
    private ThriftFactory thriftFactory;
    private HousekeeperService housekeeperService;
    private ServerSet serverSet;
    private HousekeeperXenonServiceHost host;

    @BeforeMethod
    public void setUp() throws Exception {
      config = ConfigBuilder.build(Config.class,
          ConfigTest.class.getResource("/config.yml").getPath());
      injector = TestHelper.createInjector();

      serviceNodeFactory = mock(ServiceNodeFactory.class);
      tProtocolFactory = injector.getInstance(TProtocolFactory.class);
      tTransportFactory = injector.getInstance(TTransportFactory.class);
      thriftFactory = injector.getInstance(ThriftFactory.class);
      serverSet = mock(ServerSet.class);
      host = mock(HousekeeperXenonServiceHost.class);
      housekeeperService = new HousekeeperService(serverSet, host);

      // start the server
      server = spy(new HousekeeperServer(serviceNodeFactory, tProtocolFactory,
          tTransportFactory, thriftFactory, housekeeperService, config.getThriftConfig()));

      ThriftEventHandler thriftEventHandler = mock(ThriftEventHandler.class);
      doReturn(thriftEventHandler).when(server).getThriftEventHandler();

      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            server.serve();
          } catch (Throwable e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
          }

        }
      }).start();
    }

    @AfterMethod
    public void tearDown() {
      try {
        if (server != null) {
          server.stop();
        }
      } catch (RuntimeException e) {
        // ignore the exception thrown when stop before connecting to Zookeeper
      }
    }

    @Test
    public void testThriftEndpoint() throws Throwable {
      Housekeeper.Client client =
          TestHelper.createLocalThriftClient(config);

      assertThat(client.get_status().getType(), is(StatusType.INITIALIZING));
    }
  }
}
