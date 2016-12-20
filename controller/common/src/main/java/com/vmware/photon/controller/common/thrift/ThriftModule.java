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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.host.gen.Host;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.apache.thrift.async.TAsyncSSLClient;
import org.apache.thrift.async.TAsyncSSLClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import javax.inject.Named;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Guice module for Thrift.
 */
public class ThriftModule extends AbstractModule {
  private static final Object lock = new Object();
  private volatile SecureRandom secureRandom;
  private volatile TProtocolFactory tProtocolFactory;
  private TAsyncSSLClientManager tAsyncSSLClientManager;
  private volatile ScheduledExecutorService scheduledExecutorService;
  private final SSLContext sslContext;

  public ThriftModule(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  @Override
  protected void configure() {
    install(new FactoryModuleBuilder()
        .implement(ThriftEventHandler.class, ThriftEventHandler.class)
        .implement(MultiplexedProtocolFactory.class, MultiplexedProtocolFactory.class)
        .build(ThriftFactory.class));
  }

  // The following getters lock to ensure a singleton for Thrift communication.
  // This also includes Guice Singleton annotations since these methods are
  // called by both Guice and ThriftModule.
  @Provides
  @Singleton
  @ClientPoolTimer
  ScheduledExecutorService getClientPoolTimer() {
    if (scheduledExecutorService == null)  {
      synchronized (lock) {
        if (scheduledExecutorService == null) {
          scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
      }
    }
    return scheduledExecutorService;
  }

  @Provides
  @Singleton
  SecureRandom getSecureRandom() {
    if (secureRandom == null) {
      synchronized (lock) {
        if (secureRandom == null) {
          secureRandom = new SecureRandom();
        }
      }
    }
    return secureRandom;
  }

  @Provides
  @Singleton
  TAsyncSSLClientManager getTAsyncClientManager() throws IOException {
    if (tAsyncSSLClientManager == null) {
      synchronized (lock) {
        if (tAsyncSSLClientManager == null) {
          tAsyncSSLClientManager = new TAsyncSSLClientManager();
        }
      }
    }
    return tAsyncSSLClientManager;
  }

  @Provides
  @Singleton
  SSLContext getSSLContext() {
    return this.sslContext;
  }

  @Provides
  @Singleton
  TProtocolFactory getTProtocolFactory() {
    if (tProtocolFactory == null) {
      synchronized (lock) {
        if (tProtocolFactory == null) {
          tProtocolFactory = new TCompactProtocol.Factory();
        }
      }
    }
    return tProtocolFactory;
  }

  @Provides
  @Singleton
  @Named("ClientProxyExecutor")
  ExecutorService getClientProxyExecutor() {
    return Executors.newCachedThreadPool();
  }


  /**
   * Creates a TAsyncSSLClientFactory of the given type.
   *
   * @param type the type of TAsyncSSLClientFactory to create.
   * @return
   * @throws IOException
   */
  private <T extends TAsyncSSLClient> TAsyncSSLClientFactory<T> getTAsyncSSLClientFactory(TypeLiteral<T> type)
      throws IOException {
    final TAsyncSSLClientManager clientManager = getTAsyncClientManager();
    return new TAsyncSSLClientFactory<T>(type, clientManager);
  }

  /**
   * Creates a ClientPoolFactory that builds a new ClientPool based on given servers and options.
   *
   * @return
   */
  public <T extends TAsyncSSLClient> ClientPoolFactory<T> getClientPoolFactory(
      TAsyncSSLClientFactory<T> tAsyncSSLClientFactory) {
    final SecureRandom random = getSecureRandom();
    final TProtocolFactory protocolFactory = getTProtocolFactory();
    final ScheduledExecutorService scheduledExecutorService = getClientPoolTimer();
    final ThriftFactory thriftFactory = getThriftFactory();

    return new ClientPoolFactoryImpl<T>(
        random,
        protocolFactory,
        scheduledExecutorService,
        thriftFactory,
        tAsyncSSLClientFactory,
        sslContext);
  }

  /**
   * Creates a ClientProxyFactory of the given type.
   *
   * @param type the type of ClientProxy to create.
   * @return
   */
  public <T extends TAsyncSSLClient> ClientProxyFactory<T> getClientProxyFactory(TypeLiteral<T> type) {
    final ExecutorService clientProxyExecutor = getClientProxyExecutor();

    return new ClientProxyFactoryImpl<T>(clientProxyExecutor, type);
  }

  /**
   * Creates a ThriftFactory.
   *
   * @return
   */
  private ThriftFactory getThriftFactory() {
    final TProtocolFactory protocolFactory = getTProtocolFactory();

    return new ThriftFactoryImpl(protocolFactory);
  }

  /**
   * Creates a AgentControlClientFactory of the given type.
   *
   * @return
   * @throws IOException
   */
  public AgentControlClientFactory getAgentControlClientFactory() throws IOException {
    TypeLiteral<AgentControl.AsyncSSLClient> type = new TypeLiteral<AgentControl.AsyncSSLClient>() {};
    TAsyncSSLClientFactory<AgentControl.AsyncSSLClient> tAsyncSSLClientFactory = getTAsyncSSLClientFactory(type);
    ClientPoolFactory<AgentControl.AsyncSSLClient> clientPoolFactory = getClientPoolFactory(tAsyncSSLClientFactory);
    ClientProxyFactory<AgentControl.AsyncSSLClient> clientProxyFactory = getClientProxyFactory(type);

    return new AgentControlClientFactoryImpl(clientPoolFactory, clientProxyFactory);
  }

  /**
   * Creates a HostClientFactory of the given type.
   *
   * @return
   * @throws IOException
   */
  public HostClientFactory getHostClientFactory() throws IOException {
    TypeLiteral<Host.AsyncSSLClient> type = new TypeLiteral<Host.AsyncSSLClient>() {};
    TAsyncSSLClientFactory<Host.AsyncSSLClient> tAsyncSSLClientFactory = getTAsyncSSLClientFactory(type);
    ClientPoolFactory<Host.AsyncSSLClient> clientPoolFactory = getClientPoolFactory(tAsyncSSLClientFactory);
    ClientProxyFactory<Host.AsyncSSLClient> clientProxyFactory = getClientProxyFactory(type);

    return new HostClientFactoryImpl(clientPoolFactory, clientProxyFactory);
  }

  /**
   * Implementation of HostClientFactory.
   */
  private static class HostClientFactoryImpl implements HostClientFactory {
    private ClientPoolFactory<Host.AsyncSSLClient> clientPoolFactory;
    private ClientProxyFactory<Host.AsyncSSLClient> clientProxyFactory;

    private HostClientFactoryImpl(
        ClientPoolFactory<Host.AsyncSSLClient> clientPoolFactory,
        ClientProxyFactory<Host.AsyncSSLClient> clientProxyFactory) {
      this.clientPoolFactory = clientPoolFactory;
      this.clientProxyFactory = clientProxyFactory;
    }

    @Override
    public HostClient create() {
      return new HostClient(clientProxyFactory, clientPoolFactory);
    }
  }

  /**
   * Implementation of a ClientProxyFactory.
   */
  private static class ClientProxyFactoryImpl<T extends TAsyncSSLClient> implements ClientProxyFactory<T> {
    private final ExecutorService clientProxyExecutor;
    private TypeLiteral<T> type;

    private ClientProxyFactoryImpl(final ExecutorService clientProxyExecutor, TypeLiteral<T> type) {
      this.clientProxyExecutor = clientProxyExecutor;
      this.type = type;
    }

    @Override
    public ClientProxy<T> create(ClientPool<T> clientPool) {
      return new ClientProxyImpl<>(clientProxyExecutor, type, clientPool);
    }
  }

  /**
   * Implementation of a ClientPoolFactory.
   */
  private static class ClientPoolFactoryImpl<T extends TAsyncSSLClient>  implements ClientPoolFactory<T> {
    private final SecureRandom random;
    private final TProtocolFactory protocolFactory;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ThriftFactory thriftFactory;
    private final TAsyncSSLClientFactory<T> tAsyncSSLClientFactory;
    private final SSLContext sslContext;

    private ClientPoolFactoryImpl(final SecureRandom random,
                                  final TProtocolFactory protocolFactory,
                                  final ScheduledExecutorService scheduledExecutorService,
                                  final ThriftFactory thriftFactory,
                                  final TAsyncSSLClientFactory<T> tAsyncSSLClientFactory,
                                  final SSLContext sslContext) {
      this.random = random;
      this.protocolFactory = protocolFactory;
      this.scheduledExecutorService = scheduledExecutorService;
      this.thriftFactory = thriftFactory;
      this.tAsyncSSLClientFactory = tAsyncSSLClientFactory;
      this.sslContext = sslContext;
    }

    @Override
    public ClientPool<T> create(ServerSet serverSet, ClientPoolOptions options) {
      return new ClientPoolImpl<>(
          random,
          tAsyncSSLClientFactory,
          sslContext,
          protocolFactory,
          thriftFactory,
          scheduledExecutorService,
          serverSet,
          options);
    }

    @Override
    public ClientPool<T> create(Set<InetSocketAddress> servers, ClientPoolOptions options) {
      return new BasicClientPool<>(
          random,
          tAsyncSSLClientFactory,
          sslContext,
          protocolFactory,
          thriftFactory,
          scheduledExecutorService,
          servers,
          options);
    }
  }

  /**
   * Implementation of a ThriftFactory.
   */
  private static class ThriftFactoryImpl implements ThriftFactory {
    private final TProtocolFactory protocolFactory;

    private ThriftFactoryImpl(final TProtocolFactory protocolFactory) {
      this.protocolFactory = protocolFactory;
    }

    @Override
    public ThriftEventHandler create(ServiceNodeEventHandler serviceNodeEventHandler, ServiceNode serviceNode) {
      return new ThriftEventHandler(serviceNodeEventHandler, serviceNode);
    }

    @Override
    public MultiplexedProtocolFactory create(String serviceName) {
      return new MultiplexedProtocolFactory(protocolFactory, serviceName);
    }
  }

  /**
   * Implementation of AgentControlClientFactory.
   */
  private static class AgentControlClientFactoryImpl implements AgentControlClientFactory {
    private ClientPoolFactory<AgentControl.AsyncSSLClient> clientPoolFactory;
    private ClientProxyFactory<AgentControl.AsyncSSLClient> clientProxyFactory;

    private AgentControlClientFactoryImpl(ClientPoolFactory<AgentControl.AsyncSSLClient> clientPoolFactory,
        ClientProxyFactory<AgentControl.AsyncSSLClient> clientProxyFactory) {
      this.clientPoolFactory = clientPoolFactory;
      this.clientProxyFactory = clientProxyFactory;
    }

    @Override
    public AgentControlClient create() {
      return new AgentControlClient(clientProxyFactory, clientPoolFactory);
    }
  }
}
