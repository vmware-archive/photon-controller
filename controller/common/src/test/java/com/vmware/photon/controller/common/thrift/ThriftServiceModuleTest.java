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

package com.vmware.photon.controller.common.thrift;

import com.example.echo.Echoer;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link ThriftServiceModule}.
 */
@Guice(moduleFactory = ModuleFactory.class)
public class ThriftServiceModuleTest {

  @Inject
  private ClientPoolFactory<Echoer.AsyncSSLClient> poolFactory;

  @Test
  public void testClientPoolFactory() {
    ClientPoolOptions clientPoolOptions = new ClientPoolOptions()
        .setMaxClients(1)
        .setMaxWaiters(1)
        .setTimeout(10, TimeUnit.SECONDS)
        .setServiceName("Echoer");
    ClientPool<Echoer.AsyncSSLClient> clientPool = poolFactory.create(new TestServerSet(), clientPoolOptions);
    assertThat(clientPool.getClass().toString(), is(ClientPoolImpl.class.toString()));

    clientPool = poolFactory.create(ImmutableSet.of(new InetSocketAddress("127.0.0.1", 80)), clientPoolOptions);
    assertThat(clientPool.getClass().toString(), is(BasicClientPool.class.toString()));
  }
}
