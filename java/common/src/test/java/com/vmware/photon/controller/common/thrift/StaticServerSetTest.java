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

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Tests {@link StaticServerSet}.
 */
public class StaticServerSetTest {

  @Test
  public void testAddChangeListener() throws IOException {
    InetSocketAddress server = new InetSocketAddress(80);
    StaticServerSet set = new StaticServerSet(server);
    ServerSet.ChangeListener listener = mock(ServerSet.ChangeListener.class);
    set.addChangeListener(listener);
    verify(listener).onServerAdded(server);
    verifyNoMoreInteractions(listener);
    set.close();
  }

  @Test
  public void testMultipleServers() throws IOException {
    InetSocketAddress server1 = new InetSocketAddress(80);
    InetSocketAddress server2 = new InetSocketAddress(8080);
    StaticServerSet set = new StaticServerSet(server1, server2);
    ServerSet.ChangeListener listener = mock(ServerSet.ChangeListener.class);
    set.addChangeListener(listener);
    verify(listener).onServerAdded(server1);
    verify(listener).onServerAdded(server2);
    verifyNoMoreInteractions(listener);
    set.close();
  }

  @Test
  public void testGetServers() throws IOException {
    InetSocketAddress server1 = new InetSocketAddress(80);
    InetSocketAddress server2 = new InetSocketAddress(8080);
    StaticServerSet set = new StaticServerSet(server1, server2);
    assertThat(set.getServers().contains(server1), is(true));
    assertThat(set.getServers().contains(server2), is(true));
    set.close();
  }
}
