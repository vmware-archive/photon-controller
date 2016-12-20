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

import com.example.echo.EchoRequest;
import com.example.echo.Echoer;
import com.google.common.collect.ImmutableSet;
import com.google.inject.TypeLiteral;
import org.apache.curator.test.DirectoryUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncSSLClientManager;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TSSLTransportFactory.TSSLTransportParameters;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertTrue;

import javax.net.ssl.SSLContext;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
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
  public static final String KEY_PATH = "/tmp/" + UUID.randomUUID().toString();

  private TTransportFactory transportFactory = new TFastFramedTransport.Factory();

  private TSSLTransportParameters params;
  private SSLContext sslContext = KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);

  private TServer server;
  private ThriftModule thriftModule;

  @BeforeClass
  public void beforeClass() throws Throwable {
    KeyStoreUtils.generateKeys(KEY_PATH);
  }

  @AfterClass
  public void afterClass() {
    try {
      DirectoryUtils.deleteRecursively(new File(KEY_PATH));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @BeforeMethod
  private void setUp() {
    TSSLTransportParameters params = new TSSLTransportParameters();
    params.setKeyStore(KEY_PATH + "/" + KeyStoreUtils.KEY_STORE_NAME, KeyStoreUtils.KEY_PASS);
    params.setTrustStore(KEY_PATH + "/" + KeyStoreUtils.KEY_STORE_NAME, KeyStoreUtils.KEY_PASS);
    this.params = params;
    thriftModule = new ThriftModule(sslContext);
  }

  @AfterMethod
  private void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Test
  public void testEndToEnd() throws Exception {
    TServerSocket transport = TSSLTransportFactory.getServerSocket(0, 0, InetAddress.getByName("127.0.0.1"), params);

    Echoer.Processor<EchoServer> processor = new Echoer.Processor<>(new EchoServer());
    server = new TThreadPoolServer(new TThreadPoolServer.Args(transport)
        .transportFactory(transportFactory)
        .protocolFactory(thriftModule.getTProtocolFactory())
        .processor(processor));

    // this needs to happen after thrift initialized its SSLContext
    // otherwise we will try to validate the certificates
    KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.submit(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    });

    ClientProxy<Echoer.AsyncSSLClient> clientProxy = createClientProxy(transport);

    final CountDownLatch latch = new CountDownLatch(1);
    final Echoer.AsyncSSLClient.echo_call[] result = {null};
    final Exception[] error = {null};

    Echoer.AsyncIface echoer = clientProxy.get();
    echoer.echo("Hello", new AsyncMethodCallback<Echoer.AsyncSSLClient.echo_call>() {
      @Override
      public void onComplete(Echoer.AsyncSSLClient.echo_call response) {
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

  private ClientProxy<Echoer.AsyncSSLClient> createClientProxy(TServerSocket transport) throws IOException {
    TypeLiteral<Echoer.AsyncSSLClient> type = new TypeLiteral<Echoer.AsyncSSLClient>() {};
    final TAsyncSSLClientManager clientManager = new TAsyncSSLClientManager();
    TAsyncSSLClientFactory<Echoer.AsyncSSLClient> tAsyncSSLClientFactory
      = new TAsyncSSLClientFactory<>(type, clientManager);

    InetSocketAddress localPort = new InetSocketAddress("127.0.0.1", transport.getServerSocket().getLocalPort());
    ClientPool<Echoer.AsyncSSLClient> pool = thriftModule.getClientPoolFactory(tAsyncSSLClientFactory)
        .create(ImmutableSet.of(localPort),
        new ClientPoolOptions().setMaxClients(10).setMaxWaiters(10));
    ClientProxy<Echoer.AsyncSSLClient> clientProxy = thriftModule.getClientProxyFactory(type).create(pool);

    return clientProxy;
  }

  @Test
  public void testEndToEndRpcTimeout() throws Exception {
    TServerSocket transport = TSSLTransportFactory.getServerSocket(0, 0, InetAddress.getByName("127.0.0.1"), params);
    ClientProxy<Echoer.AsyncSSLClient> clientProxy = createClientProxy(transport);

    Echoer.Processor<SleepyEchoServer> processor = new Echoer.Processor<>(new SleepyEchoServer());
    server = new TThreadPoolServer(new TThreadPoolServer.Args(transport)
        .transportFactory(transportFactory)
        .protocolFactory(thriftModule.getTProtocolFactory())
        .processor(processor));

    // this needs to happen after thrift initialized its SSLContext
    // otherwise we will try to validate the certificates
    KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.submit(new Runnable() {
      @Override
      public void run() {
        server.serve();
      }
    });

    final CountDownLatch latch = new CountDownLatch(1);
    final Echoer.AsyncSSLClient.echo_call[] result = {null};
    final Exception[] error = {null};

    Echoer.AsyncSSLClient echoer = clientProxy.get();
    echoer.setTimeout(5);
    echoer.echo("Hello", new AsyncMethodCallback<Echoer.AsyncSSLClient.echo_call>() {
      @Override
      public void onComplete(Echoer.AsyncSSLClient.echo_call response) {
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
