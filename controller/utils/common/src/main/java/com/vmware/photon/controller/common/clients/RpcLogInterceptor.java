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

package com.vmware.photon.controller.common.clients;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Intercepts @RpcMethod and provides log entries for start/end.
 */
public class RpcLogInterceptor implements MethodInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(RpcLogInterceptor.class);

  public RpcLogInterceptor(Class<?> type, Method method) {
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    String methodName = invocation.getMethod().getName();
    try {
      logger.info("Starting call to {}", methodName);
      return invocation.proceed();
    } catch (Throwable t) {
      logger.info("Caught exception during {}: {}", methodName, t);
      throw t;
    } finally {
      logger.info("Finished call to {}", methodName);
    }
  }
}
