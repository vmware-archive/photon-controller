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
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.Scheduler;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Represents scheduler managed by this root scheduler.
 */
public class ManagedScheduler {
  private static final Logger logger = LoggerFactory.getLogger(ManagedScheduler.class);

  private final Scheduler.AsyncClient client;
  private final String id;
  private final String ownerHostId;
  private final ServerSet serverSet;
  private final StaticServerSetFactory serverSetFactory;
  private final Config config;
  private final ClientPool<Scheduler.AsyncClient> clientPool;
  private Set<ResourceConstraint> resources;
  private int weight = 0;

  @Inject
  public ManagedScheduler(@Assisted("schedulerId") String id,
                          @Assisted InetSocketAddress address,
                          @Assisted("hostId")String ownerHostId,
                          Config config,
                          StaticServerSetFactory serverSetFactory,
                          ClientPoolFactory<Scheduler.AsyncClient> clientPoolFactory,
                          ClientProxyFactory<Scheduler.AsyncClient> clientProxyFactory) {
    this.id = id;
    this.ownerHostId = ownerHostId;
    this.config = config;

    this.serverSetFactory = serverSetFactory;
    serverSet = serverSetFactory.create(address);
    // TODO(vspivak): make externally configurable
    ClientPoolOptions options = new ClientPoolOptions()
        .setMaxClients(128)
        .setMaxWaiters(1024)
        .setTimeout(60, TimeUnit.SECONDS)
        .setServiceName("Scheduler");

    clientPool = clientPoolFactory.create(serverSet, options);
    ClientProxy<Scheduler.AsyncClient> clientProxy = clientProxyFactory.create(clientPool);
    client = clientProxy.get();
  }

  public String getId() {
    return id;
  }

  public String getOwnerHostId() {
    return ownerHostId;
  }

  public int getWeight() {
    return weight;
  }

  public void setWeight(int weight) {
    this.weight = weight;
  }

  public Set<ResourceConstraint> getResources() {
    return resources;
  }

  public InetSocketAddress getAddress() {
    return serverSet.getServers().iterator().next();
  }

  @Override
  protected void finalize () throws Throwable {
    if (clientPool == null || !clientPool.isClosed()) {
      logger.error("Client pool not closed!, FD(s) possibly leaked!");
    }
  }

  public void cleanUp() {
    clientPool.close();
  }

  public void setResources(ChildInfo childScheduler) {
    List<ResourceConstraint> constraints = childScheduler.getConstraints();

    if (constraints != null) {
      logConstraints(childScheduler.getId(), constraints);
      Map<ResourceConstraintType, ResourceConstraint> map = new HashMap<>();
      /*
       * Coalesce resource constraints by type
       */
      for (ResourceConstraint constraint : constraints) {
        /*
         * Look for an existing ResourceConstraint
         * with matching type
         */
        ResourceConstraint target = map.get(constraint.getType());
        if (target == null) {
          target = new ResourceConstraint(
              constraint.getType(),
              new ArrayList<>(constraint.getValues()));
          map.put(constraint.getType(), target);
        } else {
          // Found a match, merge the value lists
          target.getValues().addAll(constraint.getValues());
        }
      }

      setResources(new HashSet<>(map.values()));
    }
    setWeight(childScheduler.getWeight());
  }

  public void setResources(Set<ResourceConstraint> resources) {
    this.resources = resources;
  }

  public ListenableFuture<PlaceResponse> place(PlaceRequest request, long timeout) {
    // Clone the request and set the scheduler id
    request = new PlaceRequest(request);
    request.setScheduler_id(id);

    try {
      SettableFuture<PlaceResponse> promise = SettableFuture.create();
      client.setTimeout(timeout);
      client.place(request, new PlaceCallback(promise));
      return promise;
    } catch (TException e) {
      return Futures.immediateFailedFuture(e);
    }
  }

  public ListenableFuture<FindResponse> find(FindRequest request, long timeout) {
    // Clone the request and set the scheduler id
    request = new FindRequest(request);
    request.setScheduler_id(id);

    try {
      SettableFuture<FindResponse> promise = SettableFuture.create();
      client.setTimeout(timeout);
      client.find(request, new FindCallback(promise));
      return promise;
    } catch (TException e) {
      return Futures.immediateFailedFuture(e);
    }
  }

  private void logConstraints(String id, List<ResourceConstraint> constraints) {
    logger.info("Scheduler {} has resources:", id);
    for (ResourceConstraint constraint : constraints) {
      logger.info("Resource {}, {}", constraint.getType(), constraint.getValues());
    }
  }

  @Override
  public String toString() {
    return String.format("%s@%s", id, getAddress().toString());
  }

  static class PlaceCallback implements AsyncMethodCallback<Scheduler.AsyncClient.place_call> {
    private final SettableFuture<PlaceResponse> promise;

    private PlaceCallback(SettableFuture<PlaceResponse> promise) {
      this.promise = promise;
    }

    @Override
    public void onComplete(Scheduler.AsyncClient.place_call response) {
      try {
        promise.set(response.getResult());
      } catch (TException e) {
        promise.setException(e);
      }
    }

    @Override
    public void onError(Exception e) {
      promise.setException(e);
    }
  }

  static class FindCallback implements AsyncMethodCallback<Scheduler.AsyncClient.find_call> {
    private final SettableFuture<FindResponse> promise;

    private FindCallback(SettableFuture<FindResponse> promise) {
      this.promise = promise;
    }

    @Override
    public void onComplete(Scheduler.AsyncClient.find_call response) {
      try {
        promise.set(response.getResult());
      } catch (TException e) {
        promise.setException(e);
      }
    }

    @Override
    public void onError(Exception e) {
      promise.setException(e);
    }
  }
}
