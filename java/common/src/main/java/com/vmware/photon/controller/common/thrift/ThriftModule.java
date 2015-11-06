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
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TTransportFactory;

import javax.inject.Named;

import java.io.IOException;
import java.security.SecureRandom;
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
}
