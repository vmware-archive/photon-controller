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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import org.apache.thrift.async.TAsyncSSLClient;
import org.apache.thrift.async.TAsyncSSLClientManager;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSSLTransport;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Factory for all {@link TAsyncClient}.
 * <p/>
 * Necessary to simplify the Guice bindings.
 *
 * @param <T> async thrift client type
 */
@Singleton
public class TAsyncSSLClientFactory<T extends TAsyncSSLClient> {
  private final TAsyncSSLClientManager clientManager;
  private final Constructor<T> constructor;

  @SuppressWarnings("unchecked")
  @Inject
  TAsyncSSLClientFactory(TypeLiteral<T> type, TAsyncSSLClientManager clientManager) {
    this.clientManager = clientManager;
    try {
      this.constructor = (Constructor<T>) type.getRawType().getConstructor(
          TProtocolFactory.class, TAsyncSSLClientManager.class, TNonblockingSSLTransport.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a new {@link TAsyncClient} for bound service and given transport.
   *
   * @param protocolFactory
   * @param transport       client transport
   * @return new client
   */
  public T create(TProtocolFactory protocolFactory, TNonblockingSSLTransport transport) {
    try {
      return constructor.newInstance(protocolFactory, clientManager, transport);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
