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

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import org.apache.thrift.async.TAsyncSSLClient;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Guice module for Thrift service.
 *
 * @param <C> client type
 */
public class ThriftServiceModule<C extends TAsyncSSLClient> extends AbstractModule {
  private final TypeLiteral<C> client;

  public ThriftServiceModule(TypeLiteral<C> client) {
    this.client = client;
  }

  private static TypeLiteral<?> getTypeLiteral(Type rawType, Type... typeArguments) {
    ParameterizedType type = Types.newParameterizedType(rawType, typeArguments);
    return TypeLiteral.get(type);
  }

  @Override
  protected void configure() {
    bindClientPoolFactory();
    bindClientProxyFactory();
  }

  @SuppressWarnings("unchecked")
  private void bindClientProxyFactory() {
    TypeLiteral clientProxyFactory = getTypeLiteral(ClientProxyFactory.class, client.getType());
    TypeLiteral source = getTypeLiteral(ClientProxy.class, client.getType());
    TypeLiteral target = getTypeLiteral(ClientProxyImpl.class, client.getType());
    install(new FactoryModuleBuilder().implement(source, target).build(clientProxyFactory));
  }

  @SuppressWarnings("unchecked")
  private void bindClientPoolFactory() {
    TypeLiteral clientPoolFactory = getTypeLiteral(ClientPoolFactory.class, client.getType());
    TypeLiteral source = getTypeLiteral(ClientPool.class, client.getType());
    TypeLiteral serverSetTarget = getTypeLiteral(ClientPoolImpl.class, client.getType());
    TypeLiteral serversTarget = getTypeLiteral(BasicClientPool.class, client.getType());
    install(new FactoryModuleBuilder()
        .implement(source, serverSetTarget)
        .implement(source, Names.named("Basic"), serversTarget)
        .build(clientPoolFactory));
  }
}
