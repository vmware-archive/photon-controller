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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.codahale.metrics.MetricRegistry.name;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_HYPHEN;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Intercepts @RpcMethod and provides duration and exception metrics.
 */
public class RpcMetricInterceptor implements MethodInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(RpcMetricInterceptor.class);

  private final Timer duration;
  private final Meter exceptions;

  public RpcMetricInterceptor(Class<?> type, Method method) {
    String name = LOWER_CAMEL.to(LOWER_HYPHEN, method.getName());
    duration = DefaultMetricRegistry.REGISTRY.timer(name(type, name));
    exceptions = DefaultMetricRegistry.REGISTRY.meter(name(type, name + "-exceptions", "exceptions"));
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Stopwatch stopwatch = Stopwatch.createStarted();
    String methodName = invocation.getMethod().getName();
    try {
      logger.info("Starting call to {}", methodName);
      return invocation.proceed();
    } catch (Throwable t) {
      exceptions.mark();
      logger.info("Caught exception during {}: {}", methodName, t);
      throw t;
    } finally {
      duration.update(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
      logger.info("Finished call to {}", methodName);
    }
  }
}
