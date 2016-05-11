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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;

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
  @Override
  protected void configure() {
    install(new FactoryModuleBuilder()
        .implement(ThriftEventHandler.class, ThriftEventHandler.class)
        .implement(MultiplexedProtocolFactory.class, MultiplexedProtocolFactory.class)
        .build(ThriftFactory.class));
  }

  @Provides
  @Singleton
  @ClientPoolTimer
  public ScheduledExecutorService getClientPoolTimer() {
    return Executors.newScheduledThreadPool(1);
  }

  @Provides
  @Singleton
  public SecureRandom getSecureRandom() {
    return new SecureRandom();
  }

  @Provides
  @Singleton
  TAsyncClientManager getTAsyncClientManager() throws IOException {
    return new TAsyncClientManager();
  }

  @Provides
  @Singleton
  TProtocolFactory getTProtocolFactory() {
    return new TCompactProtocol.Factory();
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
   * @param type the type of TAsyncClientFactory to create.
   * @return
   * @throws IOException
   */
  public TAsyncClientFactory getTAsyncClientFactory(TypeLiteral type) throws IOException {
    final TAsyncClientManager clientManager = getTAsyncClientManager();
    TAsyncClientFactory tAsyncClientFactory = new TAsyncClientFactory(type, clientManager);
    return tAsyncClientFactory;
  }

  /**
   * Creates a ClientPoolFactory that builds a new ClientPool based on given servers and options.
   * @return
   */
  public ClientPoolFactory getClientPoolFactory(TAsyncClientFactory tAsyncClientFactory) {
    ClientPoolFactory clientPoolFactory = new ClientPoolFactory() {
      final SecureRandom random = getSecureRandom();
      final TProtocolFactory protocolFactory = getTProtocolFactory();
      final ScheduledExecutorService scheduledExecutorService = getClientPoolTimer();
      final ThriftFactory thriftFactory = getThriftFactory();

      @Override
      public ClientPool create(ServerSet serverSet, ClientPoolOptions options) {
        ClientPool clientPool = new ClientPoolImpl<>(random, tAsyncClientFactory, protocolFactory, thriftFactory,
            scheduledExecutorService, serverSet, options);
        return clientPool;
      }

      @Override
      public ClientPool create(Set servers, ClientPoolOptions options) {
        ClientPool clientPool = new BasicClientPool<>(random, tAsyncClientFactory, protocolFactory, thriftFactory,
            scheduledExecutorService, servers, options);
        return clientPool;
      }
    };
    return clientPoolFactory;
  }

  /**
   * Creates a ClientProxyFactory of the given type.
   * @param type the type of ClientProxy to create.
   * @return
   */
  public ClientProxyFactory getClientProxyFactory(TypeLiteral type) {
    ClientProxyFactory clientProxyFactory = new ClientProxyFactory() {
      final ExecutorService clientProxyExecutor = getClientProxyExecutor();

      @Override
      public ClientProxy create(ClientPool clientPool) {
        ClientProxy clientProxy = new ClientProxyImpl<>(clientProxyExecutor, type, clientPool);
        return clientProxy;
      }
    };
    return clientProxyFactory;
  }

  /**
   * Creates a ThriftFactory.
   * @return
   */
  public ThriftFactory getThriftFactory() {
    ThriftFactory thriftFactory = new ThriftFactory() {
      final TProtocolFactory protocolFactory = getTProtocolFactory();
      @Override
      public ThriftEventHandler create(ServiceNodeEventHandler serviceNodeEventHandler, ServiceNode serviceNode) {
        return new ThriftEventHandler(serviceNodeEventHandler, serviceNode);
      }

      @Override
      public MultiplexedProtocolFactory create(String serviceName) {
        return new MultiplexedProtocolFactory(protocolFactory, serviceName);
      }
    };
    return thriftFactory;
  }

  /**
   * Creates a HostClientFactory of the given type.
   * @param type the ClientProxy type to create.
   * @return
   * @throws IOException
   */
  public HostClientFactory getHostClientFactory(TypeLiteral type) throws IOException {
    TAsyncClientFactory tAsyncClientFactory = getTAsyncClientFactory(type);
    ClientPoolFactory clientPoolFactory = getClientPoolFactory(tAsyncClientFactory);
    ClientProxyFactory clientProxyFactory = getClientProxyFactory(type);

    HostClientFactory hostClientFactory = new HostClientFactory() {
      @Override
      public HostClient create() {
        return new HostClient(clientProxyFactory, clientPoolFactory);
      }
    };
    return hostClientFactory;
  }
}
