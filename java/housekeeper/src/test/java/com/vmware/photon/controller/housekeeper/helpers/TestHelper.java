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

import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.housekeeper.Config;
import com.vmware.photon.controller.housekeeper.HousekeeperServer;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Guice;
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

  public static Injector createInjector() {
    return Guice.createInjector(
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new TestHousekeeperModule());
  }

  public static Housekeeper.Client createLocalThriftClient(Config config)
      throws TTransportException, InterruptedException, TimeoutException {

    long timeLeft = CONNECTION_TIMEOUT_IN_MS;
    TSocket socket = new TSocket(config.getThriftConfig().getBindAddress(),
        config.getThriftConfig().getPort());
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
}
