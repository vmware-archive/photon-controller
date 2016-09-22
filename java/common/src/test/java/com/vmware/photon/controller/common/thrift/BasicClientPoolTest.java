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

import com.vmware.photon.controller.common.ssl.KeyStoreUtils;

import com.example.echo.Echoer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonBlockingSSLSocket;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.fail;

import javax.net.ssl.SSLContext;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link BasicClientPool}.
 */
public class BasicClientPoolTest extends PowerMockTestCase {


  @Mock
  private TAsyncSSLClientFactory<Echoer.AsyncSSLClient> factory;

  @Mock
  private ScheduledExecutorService executor;

  @Mock
  private TProtocolFactory protocolFactory;

  @Mock
  private ThriftFactory thriftFactory;

  private BasicClientPool<Echoer.AsyncSSLClient> pool;

  private Field socketAddressField;

  private SSLContext sslContext = KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);

  @BeforeMethod
  public void setUp() throws Throwable {
    socketAddressField = org.apache.thrift.transport.TNonBlockingSSLSocket.class.getDeclaredField("socketAddress_");
    socketAddressField.setAccessible(true);
  }

  @AfterMethod
  public void tearDown() {
    pool.close();
    assertThat(pool.getPromises().isEmpty(), is(true));
    assertThat(pool.getClientTransportMap().isEmpty(), is(true));
  }

  @DataProvider(name = "clientPoolParams")
  public Object[][] getClientPoolParams() {
    return new Object[][]{
        {1, 2, 1},
        {1, 2, 2},
        {10, 10, 3},
        {10, 10, 10},
        {10, 10, 12}
    };
  }

  @Test(dataProvider = "clientPoolParams")
  public void testAcquireClientFromClientPool(
      int poolMaxClients,
      int poolMaxWaiter,
      int serverCount)
      throws Exception {
    assertThat(serverCount > 0, is(true));
    Set<InetSocketAddress> servers = new HashSet<>();
    for (int i = 0; i < serverCount; i++) {
      servers.add(InetSocketAddress.createUnresolved("/127.0.0.1", 80 + i));
    }

    List<Echoer.AsyncSSLClient> clients = new ArrayList<>();
    List<Echoer.AsyncSSLClient> acquiredClients = new ArrayList<>();
    setupPool(poolMaxClients, poolMaxWaiter, servers, clients, acquiredClients);

    Collections.sort(acquiredClients, new Comparator<Echoer.AsyncSSLClient>() {
      @Override
      public int compare(Echoer.AsyncSSLClient c1, Echoer.AsyncSSLClient c2) {
        return c1.toString().compareTo(c2.toString());
      }
    });

    assertThat(pool.getClientTransportMap().size(), is(poolMaxClients));
    assertThat(acquiredClients, is(clients));

    try {
      Futures.get(pool.acquire(), 100, TimeUnit.MILLISECONDS, ClientPoolException.class);
      fail("acquire client should have thrown an exception");
    } catch (ClientPoolException ex) {
      assertThat(ex.getCause() instanceof TimeoutException, is(true));
    }

    assertThat(pool.getPromises().size(), is(1)); // extra promise that failed with TimeoutException
    for (Echoer.AsyncSSLClient acquiredClient : acquiredClients) {
      pool.release(acquiredClient, true);
    }

    // one available client is used to fulfill the extra promise
    assertThat(pool.getClientTransportMap().size(), is(1));
  }

  private Set<InetSocketAddress> setupPool(
      int poolMaxClients,
      int poolMaxWaiter,
      Set<InetSocketAddress> servers,
      final List<Echoer.AsyncSSLClient> clients,
      List<Echoer.AsyncSSLClient> acquiredClients)
      throws ClientPoolException {
    final Set<InetSocketAddress> usedServers = new HashSet<>();
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        assertThat(invocation.getArguments().length, is(2));
        Object socket = invocation.getArguments()[1];
        assertThat(socket.getClass().getName(), is("org.apache.thrift.transport.TNonBlockingSSLSocket"));
        usedServers.add((InetSocketAddress) socketAddressField.get(socket));
        Echoer.AsyncSSLClient client = createClient("client-" + clients.size());
        clients.add(client);
        return client;
      }
    }).when(factory).create(any(TProtocolFactory.class), any(TNonBlockingSSLSocket.class));

    ClientPoolOptions options = new ClientPoolOptions().setMaxClients(poolMaxClients).setMaxWaiters(poolMaxWaiter);

    pool = new BasicClientPool<>(
        new SecureRandom(),
        factory,
        sslContext,
        protocolFactory,
        thriftFactory,
        executor,
        servers,
        options);

    for (int i = 0; i < poolMaxClients; i++) {
      acquiredClients.add(acquireClientFromPool());
    }

    return usedServers;
  }

  private Echoer.AsyncSSLClient acquireClientFromPool() throws ClientPoolException {
    ListenableFuture<Echoer.AsyncSSLClient> futureClient = pool.acquire();
    Echoer.AsyncSSLClient actualClient =
        Futures.get(futureClient, 100, TimeUnit.MILLISECONDS, ClientPoolException.class);
    return actualClient;
  }

  private Echoer.AsyncSSLClient createClient(String name) {
    Echoer.AsyncSSLClient client = mock(Echoer.AsyncSSLClient.class);
    when(client.toString()).thenReturn(name);
    return client;
  }

}
