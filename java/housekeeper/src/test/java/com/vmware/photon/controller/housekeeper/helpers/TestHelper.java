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

package com.vmware.photon.controller.housekeeper.helpers;

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.Config;
import com.vmware.photon.controller.housekeeper.ConfigTest;
import com.vmware.photon.controller.housekeeper.HousekeeperServer;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.TimeoutException;

/**
 * Helper class for tests.
 */
public class TestHelper {

  public static final long CONNECTION_TIMEOUT_IN_MS = 6000;
  public static final long CONNECTION_RETRY_INTERVAL_IN_MS = 50;

  public static Injector createInjector(String configFileResourcePath)
      throws BadConfigException {
    Config config = ConfigBuilder.build(Config.class,
        ConfigTest.class.getResource(configFileResourcePath).getPath());
    return Guice.createInjector(
        new ZookeeperModule(config.getZookeeper()),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new TestHousekeeperModule(config));
  }

  public static Housekeeper.Client createLocalThriftClient(TestInjectedConfig config)
      throws TTransportException, InterruptedException, TimeoutException {

    long timeLeft = CONNECTION_TIMEOUT_IN_MS;
    TSocket socket = new TSocket(config.getThriftBindAddress(), config.getThriftPort());
    while (true) {
      if (timeLeft <= 0) {
        throw new TimeoutException();
      }
      timeLeft -= CONNECTION_RETRY_INTERVAL_IN_MS;

      try {
        socket.open();
      } catch (TTransportException e) {
        Thread.sleep(CONNECTION_RETRY_INTERVAL_IN_MS);
        continue;
      }
      break;
    }

    return new Housekeeper.Client(
        new TMultiplexedProtocol(
            new TCompactProtocol(new TFastFramedTransport(socket)),
            HousekeeperServer.SERVICE_NAME
        ));
  }

  public static String[] setupOperationContextIdCaptureOnSendRequest(ServiceHost host) {
    final String[] contextId = new String[1];

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Operation op = (Operation) invocation.getArguments()[0];
        contextId[0] = op.getContextId();
        return invocation.callRealMethod();
      }
    }).when(host).sendRequest(any(Operation.class));

    return contextId;
  }

  /**
   * Class for constructing config injection.
   */
  public static class TestInjectedConfig {

    private String thriftBindAddress;
    private int thriftPort;
    private String thriftRegistrationAddress;
    private String xenonBindAddress;
    private int xenonPort;
    private String xenonRegistrationAddress;
    private String xenonStoragePath;

    @Inject
    public TestInjectedConfig(Config config) {
      this.thriftBindAddress = config.getThriftConfig().getBindAddress();
      this.thriftPort = config.getThriftConfig().getPort();
      this.thriftRegistrationAddress = config.getThriftConfig().getRegistrationAddress();
      this.xenonBindAddress = config.getXenonConfig().getBindAddress();
      this.xenonPort = config.getXenonConfig().getPort();
      this.xenonRegistrationAddress = config.getXenonConfig().getRegistrationAddress();
      this.xenonStoragePath = config.getXenonConfig().getStoragePath();
    }

    public String getThriftBindAddress() {
      return this.thriftBindAddress;
    }

    public int getThriftPort() {
      return this.thriftPort;
    }

    public String getThriftRegistrationAddress() {
      return this.thriftRegistrationAddress;
    }

    public String getXenonBindAddress() {
      return this.xenonBindAddress;
    }

    public int getXenonPort() {
      return this.xenonPort;
    }

    public String getXenonRegistrationAddress() {
      return this.xenonRegistrationAddress;
    }

    public String getXenonStoragePath() {
      return this.xenonStoragePath;
    }
  }

  /**
   * Class that is used to verify the NsxClientFactory can be injected properly.
   */
  public static class TestInjectedNsxClientFactory {
    public NsxClientFactory factory;

    @Inject
    public TestInjectedNsxClientFactory(NsxClientFactory factory) {
      this.factory = factory;
    }
  }
}
