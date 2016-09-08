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

import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static com.example.echo.Echoer.AsyncSSLClient;
import static com.example.echo.Echoer.AsyncSSLClient.echo_call;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests {@link WrappedCallback}.
 */
public class WrappedCallbackTest extends PowerMockTestCase {

  @Mock
  private AsyncMethodCallback<echo_call> callback;

  @Mock
  private AsyncSSLClient client;

  @Mock
  private ClientPool<AsyncSSLClient> clientPool;

  @Mock
  private echo_call response;

  @Test
  public void testOnComplete() throws Exception {
    WrappedCallback<AsyncSSLClient, echo_call> wcb = new WrappedCallback<>(callback, client, clientPool);
    wcb.onComplete(response);

    InOrder inOrder = inOrder(client, clientPool, callback);

    inOrder.verify(clientPool).release(client, true);
    inOrder.verify(callback).onComplete(response);

    verifyNoMoreInteractions(client, clientPool, callback);
  }

  @Test
  public void testOnError() throws Exception {
    WrappedCallback<AsyncSSLClient, echo_call> wcb = new WrappedCallback<>(callback, client, clientPool);
    Exception e = new Exception("foo bar");
    wcb.onError(e);

    InOrder inOrder = inOrder(client, clientPool, callback);

    inOrder.verify(clientPool).release(client, false);
    inOrder.verify(callback).onError(e);

    verifyNoMoreInteractions(client, clientPool, callback);
  }
}
