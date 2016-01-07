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

package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.resource.gen.Locator;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.Vm;
import com.vmware.photon.controller.resource.gen.VmLocator;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.FindResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Scheduler;

import com.google.common.util.concurrent.ListenableFuture;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link ManagedScheduler}.
 */
public class ManagedSchedulerTest extends PowerMockTestCase {

  private ManagedScheduler scheduler;

  @Mock
  private StaticServerSetFactory serverSetFactory;

  @Mock
  private ClientPoolFactory<Scheduler.AsyncClient> clientPoolFactory;

  @Mock
  private ClientProxyFactory<Scheduler.AsyncClient> clientProxyFactory;

  @Mock
  private ServerSet serverSet;

  @Mock
  private ClientPool<Scheduler.AsyncClient> clientPool;

  @Mock
  private ClientProxy<Scheduler.AsyncClient> clientProxy;

  @Mock
  private Scheduler.AsyncClient client;

  @Captor
  private ArgumentCaptor<PlaceRequest> placeRequestArgument;

  @Captor
  private ArgumentCaptor<ManagedScheduler.PlaceCallback> placeCallbackArgument;

  @Captor
  private ArgumentCaptor<FindRequest> findRequestArgument;

  @Captor
  private ArgumentCaptor<ManagedScheduler.FindCallback> findCallbackArgument;

  private PlaceRequest placeRequest;

  private FindRequest findRequest;

  @BeforeMethod
  public void setUp() {
    InetSocketAddress address = new InetSocketAddress("foo", 1234);
    when(serverSetFactory.create(address)).thenReturn(serverSet);
    when(clientPoolFactory.create(eq(serverSet), any(ClientPoolOptions.class))).thenReturn(clientPool);
    when(clientProxyFactory.create(clientPool)).thenReturn(clientProxy);

    when(clientProxy.get()).thenReturn(client);

    scheduler = new ManagedScheduler("foo", address, "fooHostId", new Config(),
        serverSetFactory, clientPoolFactory, clientProxyFactory);

    Vm vm = new Vm();
    vm.setId("bar");

    Resource resource = new Resource();
    resource.setVm(vm);

    placeRequest = new PlaceRequest();
    placeRequest.setResource(resource);

    VmLocator vmLocator = new VmLocator();
    vmLocator.setId("baz");

    Locator locator = new Locator();
    locator.setVm(vmLocator);

    findRequest = new FindRequest();
    findRequest.setLocator(locator);
  }

  @Test
  public void testSuccessfulPlace() throws Exception {
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Scheduler.AsyncClient.place_call response = mock(Scheduler.AsyncClient.place_call.class);
        PlaceResponse placeResponse = new PlaceResponse();
        placeResponse.setAgent_id("baz");
        placeResponse.setResult(PlaceResultCode.OK);

        when(response.getResult()).thenReturn(placeResponse);
        placeCallbackArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).place(placeRequestArgument.capture(), placeCallbackArgument.capture());

    ListenableFuture<PlaceResponse> placeFuture = scheduler.place(placeRequest, 60000);
    PlaceResponse response = placeFuture.get();

    assertThat(response.getResult(), is(PlaceResultCode.OK));
    assertThat(response.getAgent_id(), is("baz"));

    verify(client).place(placeRequestArgument.getValue(), placeCallbackArgument.getValue());
    verify(client).setTimeout(60000);
    verifyNoMoreInteractions(client);
  }

  @Test(expectedExceptions = Exception.class)
  public void testFailedPlace() throws Exception {
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        placeCallbackArgument.getValue().onError(new Exception("something happened"));
        return null;
      }
    }).when(client).place(placeRequestArgument.capture(), placeCallbackArgument.capture());

    ListenableFuture<PlaceResponse> placeFuture = scheduler.place(placeRequest, 60000);

    verify(client).place(placeRequestArgument.getValue(), placeCallbackArgument.getValue());
    verify(client).setTimeout(60000);
    verifyNoMoreInteractions(client);

    placeFuture.get();
  }

  @Test
  public void testSuccessfulFind() throws Exception {
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Scheduler.AsyncClient.find_call response = mock(Scheduler.AsyncClient.find_call.class);
        FindResponse findResponse = new FindResponse();
        findResponse.setAgent_id("baz");
        findResponse.setResult(FindResultCode.OK);

        when(response.getResult()).thenReturn(findResponse);
        findCallbackArgument.getValue().onComplete(response);
        return null;
      }
    }).when(client).find(findRequestArgument.capture(), findCallbackArgument.capture());

    ListenableFuture<FindResponse> findFuture = scheduler.find(findRequest, 60000);
    FindResponse response = findFuture.get();

    assertThat(response.getResult(), is(FindResultCode.OK));
    assertThat(response.getAgent_id(), is("baz"));

    verify(client).find(findRequestArgument.getValue(), findCallbackArgument.getValue());
    verify(client).setTimeout(60000);
    verifyNoMoreInteractions(client);
  }

  @Test(expectedExceptions = Exception.class)
  public void testFailedFind() throws Exception {
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        findCallbackArgument.getValue().onError(new Exception("something happened"));
        return null;
      }
    }).when(client).find(findRequestArgument.capture(), findCallbackArgument.capture());

    ListenableFuture<FindResponse> findFuture = scheduler.find(findRequest, 60000);

    verify(client).find(findRequestArgument.getValue(), findCallbackArgument.getValue());
    verify(client).setTimeout(60000);
    verifyNoMoreInteractions(client);

    findFuture.get();
  }

  @Test
  public void testToString() {
    InetSocketAddress address = new InetSocketAddress("host", 1234);
    serverSet = new StaticServerSet(address);
    when(serverSetFactory.create(address)).thenReturn(serverSet);
    when(clientPoolFactory.create(eq(serverSet), any(ClientPoolOptions.class))).thenReturn(clientPool);
    when(clientProxyFactory.create(clientPool)).thenReturn(clientProxy);
    when(clientProxy.get()).thenReturn(client);
    scheduler = new ManagedScheduler("id", address, "h1", new Config(),
        serverSetFactory, clientPoolFactory, clientProxyFactory);
    assertThat(scheduler.toString(), is("id@host:1234"));
  }

  @Test
  public void testSetResources() {
    List<ResourceConstraint> hostResources = new ArrayList<>();

    // Create one datastore entry
    ResourceConstraint resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.DATASTORE);
    List<String> values = new ArrayList<>();
    values.add("Datastore1");
    values.add("Datastore2");
    resource.setValues(values);
    hostResources.add(resource);

    // Create one network entry
    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.NETWORK);
    values = new ArrayList<>();
    values.add("Vm Network1");
    values.add("Vm Network2");
    resource.setValues(values);
    hostResources.add(resource);

    // Create one datastore tag entry
    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.DATASTORE_TAG);
    values = new ArrayList<>();
    values.add("Datastore Tag 1");
    values.add("Datastore Tag 2");
    resource.setValues(values);
    hostResources.add(resource);

    // Create another datastore entry
    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.DATASTORE);
    values = new ArrayList<>();
    values.add("Datastore3");
    values.add("Datastore4");
    resource.setValues(values);
    hostResources.add(resource);

    // Create another network entry
    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.NETWORK);
    values = new ArrayList<>();
    values.add("Vm Network2");
    values.add("Vm Network3");
    resource.setValues(values);
    hostResources.add(resource);

    ChildInfo childInfo = new ChildInfo();
    childInfo.setConstraints(hostResources);

    scheduler.setResources(childInfo);

    Set<ResourceConstraint> resources = scheduler.getResources();
    assertThat(resources.size(), is(3));

    boolean foundDatastore = false;
    boolean foundTag = false;
    boolean foundNetwork = false;
    for (ResourceConstraint res : resources) {
      switch (res.getType()) {
        case DATASTORE:
          foundDatastore = true;
          checkConstraintValues(
              res,
              Arrays.asList("Datastore1",
                  "Datastore2",
                  "Datastore3",
                  "Datastore4"));
          break;
        case NETWORK:
          foundNetwork = true;
          checkConstraintValues(
              res,
              Arrays.asList("Vm Network1",
                  "Vm Network2",
                  "Vm Network2",
                  "Vm Network3"));
          break;
        case DATASTORE_TAG:
          foundTag = true;
          checkConstraintValues(
              res,
              Arrays.asList("Datastore Tag 1",
                  "Datastore Tag 2"));
          break;
      }
    }
    assertThat(foundDatastore, is(true));
    assertThat(foundTag, is(true));
    assertThat(foundNetwork, is(true));
  }

  private void checkConstraintValues(ResourceConstraint res,
                                     List<String> values) {
    List<String> inValues = res.getValues();

    for (String value : values) {
      assertThat(inValues.contains(value), is(true));
      inValues.remove(value);
    }
    assertThat(inValues.size(), is(0));
  }
}
