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

import com.google.common.util.concurrent.Futures;
import com.google.inject.TypeLiteral;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static com.example.echo.Echoer.AsyncSSLClient;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link ClientProxyImpl}.
 */
public class ClientProxyImplTest extends PowerMockTestCase {

  private TypeLiteral<AsyncSSLClient> typeLiteral = new TypeLiteral<AsyncSSLClient>() {
  };
  private ExecutorService executor = Executors.newCachedThreadPool();

  @Mock
  private ClientPool<AsyncSSLClient> clientPool;

  @Mock
  private AsyncSSLClient client;

  @Captor
  private ArgumentCaptor<String> echoArgument;

  @Captor
  private ArgumentCaptor<AsyncMethodCallback<AsyncSSLClient.echo_call>> echoHandler;

  @Test
  public void testSuccessfulProxyMethodCall() throws Exception {
    when(clientPool.acquire()).thenReturn(Futures.immediateFuture(client));
    mockCallSuccess(client);

    ClientProxyImpl<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);
    assertThat(performEchoCall(proxy.get(), "foobar"), is("foobar"));

    verify(clientPool).acquire();
    verify(clientPool).release(client, true);
    verifyNoMoreInteractions(clientPool);
  }

  @Test
  public void testFailedProxyMethodCall() throws Exception {
    when(clientPool.acquire()).thenReturn(Futures.immediateFuture(client));
    mockCallError(client, new TException("Something happened"));

    ClientProxyImpl<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);

    try {
      performEchoCall(proxy.get(), "foobar");
      fail();
    } catch (TException e) {
      assertThat(e.getMessage(), is("Something happened"));
    }

    verify(clientPool).acquire();
    verify(clientPool).release(client, false);
    verifyNoMoreInteractions(clientPool);
  }

  @Test
  public void testFailedProxyMethodCallWithApplicationException() throws Exception {
    when(clientPool.acquire()).thenReturn(Futures.immediateFuture(client));
    mockCallError(client, new TApplicationException("Something happened"));

    ClientProxyImpl<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);

    try {
      performEchoCall(proxy.get(), "foobar");
      fail();
    } catch (TException e) {
      assertThat(e.getMessage(), is("Something happened"));
    }

    verify(clientPool).acquire();
    verify(clientPool).release(client, true);
    verifyNoMoreInteractions(clientPool);
  }

  @Test
  public void testCannotAcquireClient() throws Exception {
    when(clientPool.acquire()).thenReturn(Futures.<AsyncSSLClient>immediateFailedFuture(new Exception("foo")));

    ClientProxyImpl<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);

    try {
      performEchoCall(proxy.get(), "foobar");
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage(), is("foo"));
    }

    verify(clientPool).acquire();
    verifyNoMoreInteractions(clientPool);
  }

  @Test
  public void testCallMethodWithoutCallback() throws Exception {
    ClientProxyImpl<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);

    try {
      proxy.get().getProtocolFactory();
      fail();
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
    }

    verifyNoMoreInteractions(clientPool);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSetTimeoutMultipleClients() throws Exception {
    AsyncSSLClient client2 = mock(AsyncSSLClient.class);

    //noinspection unchecked
    when(clientPool.acquire()).thenReturn(Futures.immediateFuture(client), Futures.immediateFuture(client2));
    mockCallSuccess(client);
    mockCallSuccess(client2);

    ClientProxy<AsyncSSLClient> proxy = new ClientProxyImpl<>(executor, typeLiteral, clientPool);

    AsyncSSLClient clientProxy = proxy.get();
    clientProxy.setTimeout(10);
    assertThat(performEchoCall(clientProxy, "foo"), is("foo"));
    assertThat(performEchoCall(clientProxy, "bar"), is("bar"));

    verify(clientPool, times(2)).acquire();
    verify(client).setTimeout(10);
    verify(client2).setTimeout(10);

    verify(clientPool).release(client, true);
    verify(clientPool).release(client2, true);

    verifyNoMoreInteractions(clientPool);
  }

  private void mockCallSuccess(AsyncSSLClient client) throws Exception {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        AsyncSSLClient.echo_call response = mock(AsyncSSLClient.echo_call.class);
        when(response.getResult()).thenReturn(echoArgument.getValue());
        echoHandler.getValue().onComplete(response);
        return null;
      }
    }).when(client).echo(echoArgument.capture(), echoHandler.capture());
  }

  private void mockCallError(AsyncSSLClient client, final Exception error) throws Exception {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        echoHandler.getValue().onError(error);
        return null;
      }
    }).when(client).echo(echoArgument.capture(), echoHandler.capture());
  }

  private String performEchoCall(AsyncSSLClient client, String message) throws Exception {
    final String[] result = {null};
    final Exception[] error = {null};
    final CountDownLatch done = new CountDownLatch(1);

    client.echo(message, new AsyncMethodCallback<AsyncSSLClient.echo_call>() {
      @Override
      public void onComplete(AsyncSSLClient.echo_call response) {
        try {
          result[0] = response.getResult();
        } catch (TException e) {
          error[0] = e;
        } finally {
          done.countDown();
        }
      }

      @Override
      public void onError(Exception e) {
        error[0] = e;
        done.countDown();
      }
    });

    if (!done.await(5, TimeUnit.SECONDS)) {
      throw new Exception("Call timed out");
    }

    if (error[0] != null) {
      throw error[0];
    }

    return result[0];
  }
}
