/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
package org.apache.thrift.transport;

import javax.net.ssl.SSLContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * This allows you to create a {@link TNonBlockingSSLSocket} by directly providing an sslContext.
 * The Thrift provided factory only lets you provide a Thrift based security context which is used
 * to create the {@link javax.net.ssl.SSLContext}.
 *
 */
public class SSLClientSocketFactory {
  public static TNonBlockingSSLSocket create(String host, int port, int timeout, SSLContext sslContext) {
    try {
      Constructor<?> constructor = TNonBlockingSSLSocket.class.getDeclaredConstructors()[0];
      constructor.setAccessible(true);
      return (TNonBlockingSSLSocket) constructor.newInstance(host, port, timeout, sslContext);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }
}
