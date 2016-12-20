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

import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests {@link ThriftEventHandler}.
 */
public class ThriftEventHandlerTest extends PowerMockTestCase {

  @Mock
  private ServiceNodeEventHandler serviceHandler;

  @Mock
  private ServiceNode serviceNode;

  @Mock
  private ServiceNode.Lease lease;

  @Test
  public void testPreServeTriggersOnJoin() {
    when(serviceNode.join()).thenReturn(Futures.immediateFuture(lease));
    when(lease.getExpirationFuture()).thenReturn(SettableFuture.<Void>create());

    ThriftEventHandler handler = new ThriftEventHandler(serviceHandler, serviceNode);
    handler.preServe();

    verify(serviceNode).join();
    verify(lease).getExpirationFuture();
    verify(serviceHandler).onJoin();

    verifyNoMoreInteractions(serviceHandler, serviceNode, lease);
  }

  @Test
  public void testExpirationTriggersRejoin() {
    //noinspection unchecked
    when(serviceNode.join()).thenReturn(
        Futures.immediateFuture(lease),
        SettableFuture.<ServiceNode.Lease>create()
    );

    when(lease.getExpirationFuture()).thenReturn(Futures.<Void>immediateFuture(null));

    ThriftEventHandler handler = new ThriftEventHandler(serviceHandler, serviceNode);
    handler.preServe();

    InOrder inOrder = inOrder(serviceNode, lease, serviceHandler);

    inOrder.verify(serviceNode).join();
    inOrder.verify(serviceHandler).onJoin();
    inOrder.verify(lease).getExpirationFuture();
    inOrder.verify(serviceHandler).onLeave();
    inOrder.verify(serviceNode).join();

    verifyNoMoreInteractions(serviceHandler, serviceNode, lease);
  }

  @Test
  public void testErrorJoiningTriggersRejoin() {
    //noinspection unchecked
    when(serviceNode.join()).thenReturn(
        Futures.<ServiceNode.Lease>immediateFailedFuture(new Exception("can't join")),
        SettableFuture.<ServiceNode.Lease>create());
    when(lease.getExpirationFuture()).thenReturn(SettableFuture.<Void>create());

    ThriftEventHandler handler = new ThriftEventHandler(serviceHandler, serviceNode);
    handler.preServe();

    InOrder inOrder = inOrder(serviceNode, lease, serviceHandler);

    inOrder.verify(serviceNode, times(2)).join();

    verifyNoMoreInteractions(serviceHandler, serviceNode, lease);
  }

  @Test
  public void testErrorOnExpirationFutureTriggersRejoin() throws Exception {
    //noinspection unchecked
    when(serviceNode.join()).thenReturn(
        Futures.<ServiceNode.Lease>immediateFuture(lease),
        SettableFuture.<ServiceNode.Lease>create());

    when(lease.getExpirationFuture()).thenReturn(Futures.<Void>immediateFailedFuture(new Exception("boom")));

    ThriftEventHandler handler = new ThriftEventHandler(serviceHandler, serviceNode);
    handler.preServe();

    InOrder inOrder = inOrder(serviceNode, lease, serviceHandler);

    inOrder.verify(serviceNode).join();
    inOrder.verify(serviceHandler).onJoin();
    inOrder.verify(lease).getExpirationFuture();
    inOrder.verify(serviceHandler).onLeave();
    inOrder.verify(serviceNode).join();

    verifyNoMoreInteractions(serviceHandler, serviceNode, lease);
  }
}
