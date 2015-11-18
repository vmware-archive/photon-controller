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

import com.example.echo.EchoRequest;
import com.example.echo.Echoer;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests Thrift RPC end-to-end.
 */
@Guice(moduleFactory = ModuleFactory.class)
public class EndToEndTest {
  private static final long AWAIT_TIMEOUT = TimeUnit.SECONDS.toSeconds(5);

  @Inject
  private ClientProxyFactory<Echoer.AsyncClient> proxyFactory;

  @Inject
  private ClientPoolFactory<Echoer.AsyncClient> poolFactory;

  @Inject
  private TTransportFactory transportFactory;

  @Inject
  private TProtocolFactory protocolFactory;

  private TServer server;

  @AfterMethod
  private void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Test
  public void testEndToEnd() throws TException, InterruptedException {
    TServerSocket transport = new TServerSocket(0);
    Echoer.Processor<EchoServer> processor = new Echoer.Processor<>(new EchoServer());
    server = new TThreadPoolServer(new TThreadPoolServer.Args(transport)
        .transportFactory(transportFactory)
        .protocolFactory(protocolFactory)
        .processor(processor));


    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.submit(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    });

    InetSocketAddress localPort = new InetSocketAddress(transport.getServerSocket().getLocalPort());
    ClientPool<Echoer.AsyncClient> pool = poolFactory.create(new StaticServerSet(localPort),
        new ClientPoolOptions().setMaxClients(10).setMaxWaiters(10));
    ClientProxy<Echoer.AsyncClient> clientProxy = proxyFactory.create(pool);

    final CountDownLatch latch = new CountDownLatch(1);
    final Echoer.AsyncClient.echo_call[] result = {null};
    final Exception[] error = {null};

    Echoer.AsyncIface echoer = clientProxy.get();
    echoer.echo("Hello", new AsyncMethodCallback<Echoer.AsyncClient.echo_call>() {
      @Override
      public void onComplete(Echoer.AsyncClient.echo_call response) {
        result[0] = response;
        latch.countDown();
      }

      @Override
      public void onError(Exception exception) {
        error[0] = exception;
        latch.countDown();
      }
    });

    latch.await(AWAIT_TIMEOUT, TimeUnit.SECONDS);

    assertThat(error[0], is(nullValue()));
    assertThat(result[0].getResult(), is("Echoed: Hello"));
  }

  @Test
  public void testEndToEndAcquireTimeout() throws TException, InterruptedException {
    ClientPool<Echoer.AsyncClient> pool = poolFactory.create(new StaticServerSet(),
        new ClientPoolOptions().setMaxClients(10).setMaxWaiters(10).setTimeout(1, TimeUnit.MILLISECONDS));
    ClientProxy<Echoer.AsyncClient> clientProxy = proxyFactory.create(pool);

    final CountDownLatch latch = new CountDownLatch(1);
    final Echoer.AsyncClient.echo_call[] result = {null};
    final Exception[] error = {null};

    Echoer.AsyncIface echoer = clientProxy.get();
    echoer.echo("Hello", new AsyncMethodCallback<Echoer.AsyncClient.echo_call>() {
      @Override
      public void onComplete(Echoer.AsyncClient.echo_call response) {
        result[0] = response;
        latch.countDown();
      }

      @Override
      public void onError(Exception exception) {
        error[0] = exception;
        latch.countDown();
      }
    });

    latch.await(AWAIT_TIMEOUT, TimeUnit.SECONDS);

    assertThat(error[0], is(instanceOf(ClientPoolException.class)));
    assertTrue(error[0].getMessage().contains("Timeout"), "Expecting 'Timeout' in error message");
    assertThat(result[0], is(nullValue()));
  }

  @Test
  public void testEndToEndRpcTimeout() throws Exception {
    TServerSocket transport = new TServerSocket(0);
    Echoer.Processor<SleepyEchoServer> processor = new Echoer.Processor<>(new SleepyEchoServer());
    server = new TThreadPoolServer(new TThreadPoolServer.Args(transport)
        .transportFactory(transportFactory)
        .protocolFactory(protocolFactory)
        .processor(processor));

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.submit(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    });

    InetSocketAddress localPort = new InetSocketAddress(transport.getServerSocket().getLocalPort());
    ClientPool<Echoer.AsyncClient> pool = poolFactory.create(new StaticServerSet(localPort),
        new ClientPoolOptions().setMaxClients(10).setMaxWaiters(10));
    ClientProxy<Echoer.AsyncClient> clientProxy = proxyFactory.create(pool);

    final CountDownLatch latch = new CountDownLatch(1);
    final Echoer.AsyncClient.echo_call[] result = {null};
    final Exception[] error = {null};

    Echoer.AsyncClient echoer = clientProxy.get();
    echoer.setTimeout(5);
    echoer.echo("Hello", new AsyncMethodCallback<Echoer.AsyncClient.echo_call>() {
      @Override
      public void onComplete(Echoer.AsyncClient.echo_call response) {
        result[0] = response;
        latch.countDown();
      }

      @Override
      public void onError(Exception exception) {
        error[0] = exception;
        latch.countDown();
      }
    });

    latch.await(AWAIT_TIMEOUT, TimeUnit.SECONDS);

    assertThat(error[0], is(instanceOf(TimeoutException.class)));
    assertTrue(error[0].getMessage().contains("timed out"), "Expecting 'timed out' in error message");
    assertThat(result[0], is(nullValue()));
  }

  /**
   * Sample Echo server.
   */
  public static class EchoServer implements Echoer.Iface {
    @Override
    public String echo(String message) throws TException {
      return "Echoed: " + message;
    }

    @Override
    public String tracedEcho(EchoRequest request) throws TException {
      return echo(request.getMessage());
    }
  }

  /**
   * Sleepy Echo server.
   */
  public static class SleepyEchoServer implements Echoer.Iface {
    @Override
    public String echo(String message) throws TException {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return "Echoed: " + message;
    }

    @Override
    public String tracedEcho(EchoRequest request) throws TException {
      return echo(request.getMessage());
    }
  }
}
