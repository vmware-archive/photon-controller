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

package com.vmware.photon.controller.common.metrics;

import com.vmware.photon.controller.common.clients.RpcClient;
import com.vmware.photon.controller.common.clients.RpcMethod;

import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import java.lang.reflect.Method;

/**
 * Listens for any @RpcClient types and binds the {@link RpcMetricInterceptor} around all the @RpcMethod methods.
 */
public class RpcMetricListener implements TypeListener {

  @Override
  public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
    if (type.getRawType().isAnnotationPresent(RpcClient.class)) {
      type.getRawType().getSimpleName();

      for (Method method : type.getRawType().getDeclaredMethods()) {
        if (method.isAnnotationPresent(RpcMethod.class)) {
          encounter.bindInterceptor(Matchers.only(method), new RpcMetricInterceptor(type.getRawType(), method));
        }
      }
    }
  }
}
