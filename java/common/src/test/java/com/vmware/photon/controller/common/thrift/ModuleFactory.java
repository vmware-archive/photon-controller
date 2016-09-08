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
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import org.testng.IModuleFactory;
import org.testng.ITestContext;

import javax.net.ssl.SSLContext;

import java.util.UUID;

/**
 * Guice module factory for testing.
 */
public class ModuleFactory implements IModuleFactory {

  public static final String KEY_PATH = "/tmp/" + UUID.randomUUID().toString();

  @Override
  public Module createModule(ITestContext context, Class<?> testClass) {
    SSLContext sslContext = KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);
    return Modules.combine(
        new ThriftModule(sslContext),
        new ThriftServiceModule<>(
            new TypeLiteral<Echoer.AsyncSSLClient>() {
            }
        ),
        new TracingTestModule());
  }

  /**
   * Guice module for tracing tests.
   */
  public static class TracingTestModule extends AbstractModule {
    @Override
    protected void configure() {
    }
  }
}
