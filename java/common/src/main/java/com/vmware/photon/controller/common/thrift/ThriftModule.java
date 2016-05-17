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
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TTransportFactory;

import javax.inject.Named;

import java.io.IOException;
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
  private volatile TAsyncClientManager tAsyncClientManager;
  private volatile ScheduledExecutorService scheduledExecutorService;

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
  TAsyncClientManager getTAsyncClientManager() throws IOException {
    if (tAsyncClientManager == null) {
      synchronized (lock) {
        if (tAsyncClientManager == null) {
          tAsyncClientManager = new TAsyncClientManager();
        }
      }
    }
    return tAsyncClientManager;
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
  TTransportFactory getTTransportFactory() {
    return new TFastFramedTransport.Factory();
  }

  @Provides
  @Singleton
  @Named("ClientProxyExecutor")
  ExecutorService getClientProxyExecutor() {
    return Executors.newCachedThreadPool();
  }

  /**
   * Creates a TAsyncClientFactory of the given type.
   *
   * @param type the type of TAsyncClientFactory to create.
   * @return
   * @throws IOException
   */
  private TAsyncClientFactory getTAsyncClientFactory(TypeLiteral type) throws IOException {
    final TAsyncClientManager clientManager = getTAsyncClientManager();
    return new TAsyncClientFactory<>(type, clientManager);
  }

  /**
   * Creates a ClientPoolFactory that builds a new ClientPool based on given servers and options.
   *
   * @return
   */
  private ClientPoolFactory getClientPoolFactory(TAsyncClientFactory tAsyncClientFactory) {
    final SecureRandom random = getSecureRandom();
    final TProtocolFactory protocolFactory = getTProtocolFactory();
    final ScheduledExecutorService scheduledExecutorService = getClientPoolTimer();
    final ThriftFactory thriftFactory = getThriftFactory();

    return new ClientPoolFactoryImpl(random, protocolFactory, scheduledExecutorService, thriftFactory,
        tAsyncClientFactory);
  }

  /**
   * Creates a ClientProxyFactory of the given type.
   *
   * @param type the type of ClientProxy to create.
   * @return
   */
  private ClientProxyFactory getClientProxyFactory(TypeLiteral type) {
    final ExecutorService clientProxyExecutor = getClientProxyExecutor();

    return new ClientProxyFactoryImpl(clientProxyExecutor, type);
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
    TypeLiteral type = new TypeLiteral<AgentControl.AsyncClient>() {};
    TAsyncClientFactory tAsyncClientFactory = getTAsyncClientFactory(type);
    ClientPoolFactory clientPoolFactory = getClientPoolFactory(tAsyncClientFactory);
    ClientProxyFactory clientProxyFactory = getClientProxyFactory(type);

    return new AgentControlClientFactoryImpl(clientPoolFactory, clientProxyFactory);
  }

  /**
   * Creates a HostClientFactory of the given type.
   *
   * @return
   * @throws IOException
   */
  public HostClientFactory getHostClientFactory() throws IOException {
    TypeLiteral type = new TypeLiteral<Host.AsyncClient>() {};
    TAsyncClientFactory tAsyncClientFactory = getTAsyncClientFactory(type);
    ClientPoolFactory clientPoolFactory = getClientPoolFactory(tAsyncClientFactory);
    ClientProxyFactory clientProxyFactory = getClientProxyFactory(type);

    return new HostClientFactoryImpl(clientPoolFactory, clientProxyFactory);
  }

  /**
   * Implementation of HostClientFactory.
   */
  private static class HostClientFactoryImpl implements HostClientFactory {
    private ClientPoolFactory clientPoolFactory;
    private ClientProxyFactory clientProxyFactory;

    private HostClientFactoryImpl(ClientPoolFactory clientPoolFactory, ClientProxyFactory clientProxyFactory) {
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
  private static class ClientProxyFactoryImpl implements ClientProxyFactory {
    private final ExecutorService clientProxyExecutor;
    private TypeLiteral type;

    private ClientProxyFactoryImpl(final ExecutorService clientProxyExecutor, TypeLiteral type) {
      this.clientProxyExecutor = clientProxyExecutor;
      this.type = type;
    }

    @Override
    public ClientProxy create(ClientPool clientPool) {
      return new ClientProxyImpl<>(clientProxyExecutor, type, clientPool);
    }
  }

  /**
   * Implementation of a ClientPoolFactory.
   */
  private static class ClientPoolFactoryImpl implements ClientPoolFactory {
    private final SecureRandom random;
    private final TProtocolFactory protocolFactory;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ThriftFactory thriftFactory;
    private final TAsyncClientFactory tAsyncClientFactory;

    private ClientPoolFactoryImpl(final SecureRandom random, final TProtocolFactory protocolFactory,
                                  final ScheduledExecutorService scheduledExecutorService,
                                  final ThriftFactory thriftFactory, final TAsyncClientFactory tAsyncClientFactory) {
      this.random = random;
      this.protocolFactory = protocolFactory;
      this.scheduledExecutorService = scheduledExecutorService;
      this.thriftFactory = thriftFactory;
      this.tAsyncClientFactory = tAsyncClientFactory;
    }

    @Override
    public ClientPool create(ServerSet serverSet, ClientPoolOptions options) {
      return new ClientPoolImpl<>(random, tAsyncClientFactory, protocolFactory, thriftFactory,
          scheduledExecutorService, serverSet, options);
    }

    @Override
    public ClientPool create(Set servers, ClientPoolOptions options) {
      return new BasicClientPool<>(random, tAsyncClientFactory, protocolFactory, thriftFactory,
          scheduledExecutorService, servers, options);
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
    private ClientPoolFactory clientPoolFactory;
    private ClientProxyFactory clientProxyFactory;

    private AgentControlClientFactoryImpl(ClientPoolFactory clientPoolFactory, ClientProxyFactory clientProxyFactory) {
      this.clientPoolFactory = clientPoolFactory;
      this.clientProxyFactory = clientProxyFactory;
    }

    @Override
    public AgentControlClient create() {
      return new AgentControlClient(clientProxyFactory, clientPoolFactory);
    }
  }
}
