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

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.tracing.gen.TracingInfo;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.apache.thrift.TBase;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.inject.Named;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of {@link ClientProxy}.
 * It's NOT thread-safe (as we don't provide atomicity for setting timeout and using that same timeout in the client
 * acquired from the pool).
 *
 * @param <C> async thrift client type
 */
public class ClientProxyImpl<C extends TAsyncClient> implements ClientProxy<C> {

  private static final Logger logger = LoggerFactory.getLogger(ClientProxyImpl.class);

  private final ExecutorService executor;
  private final ClientPool<C> clientPool;
  private final Enhancer enhancer;
  private long timeout;

  @Inject
  public ClientProxyImpl(@Named("ClientProxyExecutor") ExecutorService executor,
                         TypeLiteral<C> type,
                         @Assisted final ClientPool<C> clientPool) {
    this.executor = new ClientProxyExecutor(executor);
    this.clientPool = clientPool;
    this.timeout = 0;

    this.enhancer = new Enhancer();
    this.enhancer.setSuperclass(type.getRawType());

    MethodInterceptor interceptor = createMethodInterceptor();
    this.enhancer.setCallback(interceptor);
    this.enhancer.setCallbackType(interceptor.getClass());
  }

  /**
   * #get returns a proxy object that implements interface C. Every time method is called on the proxy object,
   * it gets new client from the pool and calls the requested method on that client. Client pool acquisition is
   * done asynchronously, so calls never block, thus keeping Thrift async client semantics intact.
   * <p>
   * If the last argument to the original method is a callback (which it usually is,
   * with an exception of 'setTimeout'), it gets wrapped with {@link WrappedCallback}. Wrapper keeps track of
   * any additional context that needs to be operated on when original callback is fired (e.g. releasing client
   * back to the pool).
   * <p>
   * If the original method is 'setTimeout', ClientProxyImpl just saves timeout in its own state and applies it
   * to the actual client before performing any subsequent calls.
   *
   * @return C
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized C get() {
    return (C) this.enhancer.create(
        new Class[]{TProtocolFactory.class, TAsyncClientManager.class, TNonblockingTransport.class},
        new Object[]{null, null, null});
  }

  private MethodInterceptor createMethodInterceptor() {
    return (object, method, args, methodProxy) -> {

      // We only allow a subset of AsyncClient methods to be called, namely those that have AsyncMethodCallback as
      // a last parameter. However we need to be able to set RPC timeouts for individual calls using
      // AsyncClient#setTimeout. We can't call setTimeout on the proxied client object because there's no
      // guarantee  that the following calls will acquire the same client object from the pool,
      // hence the special handling below.
      final String methodName = method.getName();
      if (methodName.equals("setTimeout") && args.length == 1) {
        timeout = (long) args[0];
        logger.debug("Timeout acquired for the client {}", timeout);
        return null;
      }

      final AsyncMethodCallback callback = getCallback(args);
      ListenableFuture<C> futureClient = clientPool.acquire();

      Futures.addCallback(futureClient, new FutureCallback<C>() {
        @Override
        public void onSuccess(C client) {
          client.setTimeout(timeout);
          logger.debug("Timeout set for the client {}", timeout);

          AsyncMethodCallback wrappedCallback = wrapCallback(client, callback);
          args[args.length - 1] = wrappedCallback;
          setupTracing(method, args);

          try {
            method.invoke(client, args);
          } catch (Throwable e) {
            logger.error("Error invoking method {}", methodName, e);
            handleException(wrappedCallback, e);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          logger.error("Exception during acquiring client for method {}", methodName, t);
          handleException(callback, t);
        }
      }, executor);

      return null;
    };
  }

  /**
   * Extract the current request ID from the Logging MDC, and convert it to
   * a TracingInfo. This will be passed through Thrift so that we can preserve
   * the request ID for logging purposes.
   */
  private TracingInfo getRequestTracingInfo() {
    TracingInfo tracingInfo = new TracingInfo();
    String requestId = LoggingUtils.getRequestId();
    if (requestId != null) {
      tracingInfo.setRequest_id(requestId);
    }
    return tracingInfo;
  }

  /**
   * Sets up Thrift request tracing if method supports it.
   *
   * @param method Method being called
   * @param args   Method arguments
   */
  private void setupTracing(Method method, Object[] args) {
    Method tracingMethod = extractTracingMethod(args);

    if (tracingMethod == null) {
      return;
    }

    try {
      tracingMethod.invoke(args[0], getRequestTracingInfo());
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private Method extractTracingMethod(Object[] args) {
    if (args.length < 1 || !(args[0] instanceof TBase)) {
      // No tracing possible, no request provided
      return null;
    }

    try {
      return args[0].getClass().getMethod("setTracing_info", TracingInfo.class);
    } catch (NoSuchMethodException e) {
      // This particular request type doesn't have TracingInfo field
      return null;
    }
  }

  private void handleException(AsyncMethodCallback callback, Throwable cause) {
    checkNotNull(callback);

    if (cause instanceof Exception) {
      callback.onError((Exception) cause);
    } else {
      callback.onError(new RuntimeException(cause));
    }
  }

  @SuppressWarnings("unchecked")
  private AsyncMethodCallback wrapCallback(C client, AsyncMethodCallback callback) {
    return new WrappedCallback<C, Object>(checkNotNull(callback), client, clientPool);
  }

  private AsyncMethodCallback getCallback(Object[] args) {
    if (args.length > 0 && args[args.length - 1] instanceof AsyncMethodCallback) {
      return (AsyncMethodCallback) args[args.length - 1];
    }

    throw new IllegalArgumentException("ClientProxy only proxies methods " +
        "that have AsyncMethodCallback as their last argument (the only exception is 'setTimeout')");
  }
}
