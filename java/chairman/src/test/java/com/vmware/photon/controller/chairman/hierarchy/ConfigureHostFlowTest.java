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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.ConfigureResultCode;
import static com.vmware.photon.controller.host.gen.Host.AsyncClient;

import org.apache.thrift.TException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.io.IOException;

/**
 * Tests {@link ConfigureHostFlowTest}.
 */
public class ConfigureHostFlowTest extends PowerMockTestCase {

  @Mock
  private ClientPoolFactory<AsyncClient> clientPoolFactory;

  @Mock
  private ClientProxyFactory<AsyncClient> clientProxyFactory;

  @Mock
  private ClientPool<AsyncClient> clientPool;

  @Mock
  private ClientProxy<AsyncClient> clientProxy;

  @Mock
  private AsyncClient client;

  @Mock
  private DataDictionary rolesDictionary;

  @Captor
  private ArgumentCaptor<ConfigureRequest> configureRequestArgument;

  @Captor
  private ArgumentCaptor<ConfigureHostFlow.ConfigureResponseHandler> handlerArgument;

  private Host host;

  @BeforeMethod
  public void setUp() {
    host = new Host("foo", new AvailabilityZone("bar"), "foo", 1234);
    // Assume host has been persisted to zk
    host.setDirty(false);
    when(clientPoolFactory.create(any(ServerSet.class), isA(ClientPoolOptions.class))).thenReturn(clientPool);
    when(clientProxyFactory.create(clientPool)).thenReturn(clientProxy);
    when(clientProxy.get()).thenReturn(client);
  }

  @Test
  public void testConfigureSuccess() throws Exception {
    Scheduler sc1 = new Scheduler("sc-1");
    Scheduler sc2 = new Scheduler("sc-2");
    Scheduler sc3 = new Scheduler("sc-3");

    sc1.addHost(host); // this host's scheduler

    sc2.setOwner(host); // 2 schedulers running on this host
    sc3.setOwner(host);

    sc2.addChild(sc2); // one branch scheduler
    sc1.addChild(sc3); // ...and one leaf scheduler

    sc3.addHost(new Host("baz", new AvailabilityZone("zazzle"), "baz", 1234));

    // Simulating the successful async call by capturing arguments and manually calling onComplete on handler
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        AsyncClient.configure_call response = mock(AsyncClient.configure_call.class);
        when(response.getResult()).thenReturn(new ConfigureResponse(ConfigureResultCode.OK));
        handlerArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).configure(configureRequestArgument.capture(), handlerArgument.capture());

    assertThat(host.isConfigured(), is(false));
    // Assume it is not dirty (persisted to zk)
    host.setDirty(false);
    getFlow(host).run();
    assertThat(host.isConfigured(), is(true));

    ConfigureRequest request = configureRequestArgument.getValue();

    assertThat(request.getScheduler(), is("sc-1"));
    assertThat(request.getRoles().getSchedulers(), is(notNullValue()));
    assertThat(request.getRoles().getSchedulers().size(), is(2));

    SchedulerRole leaf = null;
    SchedulerRole branch = null;
    if (request.getRoles().getSchedulers().get(0).getId().equals("sc-3")) {
      leaf = request.getRoles().getSchedulers().get(0);
      branch = request.getRoles().getSchedulers().get(1);
    } else {
      branch = request.getRoles().getSchedulers().get(0);
      leaf = request.getRoles().getSchedulers().get(1);
    }

    // branch: SchedulerRole(id:sc-2, parent_id:sc-2, schedulers:[sc-2],
    //           scheduler_children:[ChildInfo(id:sc-2, address:foo, port:1234)])
    // leaf  : SchedulerRole(id:sc-3, parent_id:sc-1, hosts:[baz],
    //           host_children:[ChildInfo(id:baz, address:baz, port:1234)])]))
    assertThat(leaf.getId(), is("sc-3"));
    assertThat(leaf.getParent_id(), is("sc-1"));
    assertThat(leaf.getHost_children().size(), is(1));
    assertThat(leaf.getHost_children().get(0).getId(), is("baz"));
    assertThat(leaf.getHost_children().get(0).getAddress(), is("baz"));
    assertThat(leaf.getHost_children().get(0).getPort(), is(1234));

    assertThat(branch.getId(), is("sc-2"));
    assertThat(branch.getParent_id(), is("sc-2"));
    assertThat(branch.getScheduler_children().size(), is(1));
    assertThat(branch.getScheduler_children().get(0).getId(), is("sc-2"));
    assertThat(branch.getScheduler_children().get(0).getAddress(), is("foo"));
    assertThat(branch.getScheduler_children().get(0).getPort(), is(1234));

    verify(client).setTimeout(anyInt());
    verify(client).configure(any(ConfigureRequest.class), any(ConfigureHostFlow.ConfigureResponseHandler.class));
    verify(clientPool).close();

    verifyNoMoreInteractions(client);
  }

  @Test
  public void testConfigureIfMissing() throws Exception {

    Scheduler scheduler = new Scheduler("sc-1");
    scheduler.addHost(host);
    host.setDirty(false);
    host.setMissing(true);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        AsyncClient.configure_call response = mock(AsyncClient.configure_call.class);
        when(response.getResult()).thenReturn(new ConfigureResponse(ConfigureResultCode.OK));
        handlerArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).configure(configureRequestArgument.capture(), handlerArgument.capture());

    getFlow(host).run();
    assertThat(host.isConfigured(), is(true));
  }

  @Test
  public void testConfigureNonOkResponse() throws Exception {
    Scheduler scheduler = new Scheduler("sc-1");
    scheduler.addHost(host);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        AsyncClient.configure_call response = mock(AsyncClient.configure_call.class);
        when(response.getResult()).thenReturn(new ConfigureResponse(ConfigureResultCode.SYSTEM_ERROR));
        handlerArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).configure(configureRequestArgument.capture(), handlerArgument.capture());

    assertThat(host.isConfigured(), is(false));
    verify(rolesDictionary, never()).write(any(String.class), any(byte[].class));

    getFlow(host).run();
    assertThat(host.isConfigured(), is(false));

    verify(clientPool).close();
  }

  @Test
  public void testConfigureError() throws TException, IOException, Exception {
    Scheduler scheduler = new Scheduler("sc-1");
    scheduler.addHost(host);

    doThrow(new TException("Not today"))
        .when(client)
        .configure(any(ConfigureRequest.class), any(ConfigureHostFlow.ConfigureResponseHandler.class));

    assertThat(host.isConfigured(), is(false));
    getFlow(host).run();

    assertThat(host.isConfigured(), is(false));
    verify(rolesDictionary, never()).write(any(String.class), any(byte[].class));
    verify(clientPool).close();
  }

  @Test
  public void testConfigureRpcException() throws TException, IOException, Exception {
    Scheduler scheduler = new Scheduler("sc-1");
    scheduler.addHost(host);

    // Simulating the failed async call by capturing arguments and manually calling onError on handler
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        handlerArgument.getValue().onError(new Exception("Foo became bar too soon"));
        return null;
      }
    }).when(client).configure(configureRequestArgument.capture(), handlerArgument.capture());

    assertThat(host.isConfigured(), is(false));
    getFlow(host).run();

    assertThat(host.isConfigured(), is(false));
    verify(rolesDictionary, never()).write(any(String.class), any(byte[].class));
    verify(clientPool).close();
  }

  @Test
  public void testConfigureRpcFailure() throws TException, IOException, Exception {
    Scheduler scheduler = new Scheduler("sc-1");
    scheduler.addHost(host);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        AsyncClient.configure_call response = mock(AsyncClient.configure_call.class);
        when(response.getResult()).thenThrow(new TException("Something happened"));
        handlerArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).configure(configureRequestArgument.capture(), handlerArgument.capture());

    assertThat(host.isConfigured(), is(false));
    getFlow(host).run();

    assertThat(host.isConfigured(), is(false));
    verify(rolesDictionary, never()).write(any(String.class), any(byte[].class));
    verify(clientPool).close();
  }

  private ConfigureHostFlow getFlow(Host host) {
    ConfigureRequest req = HierarchyUtils.getConfigureRequest(host);
    return new ConfigureHostFlow(clientPoolFactory, clientProxyFactory, host, req, rolesDictionary);
  }

}
