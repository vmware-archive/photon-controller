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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.HostInfo;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.SchedulerInfo;
import com.vmware.photon.controller.api.VmInfo;
import com.vmware.photon.controller.apife.resources.routes.TopologyResourceRoutes;
import com.vmware.photon.controller.common.clients.ChairmanClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.host.gen.GetResourcesRequest;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.roles.gen.SchedulerEntry;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TODO(vspivak): remove demo ware.
 */
@Path(TopologyResourceRoutes.API)
@Api(value = TopologyResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TopologyResource {

  private static final Logger logger = LoggerFactory.getLogger(TopologyResource.class);

  private final ChairmanClient chairmanClient;
  private final ZookeeperServerSetFactory serverSetFactory;
  private final ClientProxyFactory<Host.AsyncClient> clientProxyFactory;
  private final ClientPoolFactory<Host.AsyncClient> clientPoolFactory;

  @Inject
  public TopologyResource(ChairmanClient chairmanClient,
                          ZookeeperServerSetFactory serverSetFactory,
                          ClientProxyFactory<Host.AsyncClient> clientProxyFactory,
                          ClientPoolFactory<Host.AsyncClient> clientPoolFactory) {
    this.chairmanClient = chairmanClient;
    this.serverSetFactory = serverSetFactory;
    this.clientProxyFactory = clientProxyFactory;
    this.clientPoolFactory = clientPoolFactory;
  }

  @GET
  @ApiOperation(value = "Get topology of hosts, schedulers and vms",
      response = HostInfo.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Get topology from schedulers")})
  public Response get() throws RpcException, InterruptedException, TException, IOException {
    List<SchedulerEntry> schedulers = chairmanClient.getSchedulers().getSchedulers();

    List<String> agents = new ArrayList<>();
    ListMultimap<String, SchedulerRole> schedulerMap = ArrayListMultimap.create();
    for (SchedulerEntry entry : schedulers) {
      agents.addAll(entry.getRole().getHosts());
      schedulerMap.put(entry.getAgent(), entry.getRole());
    }

    ConcurrentHashMap<String, List<Resource>> resources = new ConcurrentHashMap<>();
    CountDownLatch done = new CountDownLatch(agents.size());
    for (String agent : agents) {
      getAgentResources(resources, done, agent);
    }

    done.await(32, TimeUnit.SECONDS);

    List<HostInfo> result = new ArrayList<>();

    for (String agentId : agents) {
      List<VmInfo> respondedVms = new ArrayList<>();

      for (Resource resource : resources.get(agentId)) {
        VmInfo vmInfo = new VmInfo();
        vmInfo.setId(resource.getVm().getId());
        vmInfo.setState(resource.getVm().getState().toString());
        vmInfo.setFlavor(resource.getVm().getFlavor());

        respondedVms.add(vmInfo);
      }

      List<SchedulerInfo> respondedSchedulers = new ArrayList<>();

      for (SchedulerRole schedulerRole : schedulerMap.get(agentId)) {
        SchedulerInfo schedulerInfo = new SchedulerInfo();
        schedulerInfo.setId(schedulerRole.getId());
        schedulerInfo.setHosts(schedulerRole.getHosts());
        schedulerInfo.setSchedulers(schedulerRole.getSchedulers());
        schedulerInfo.setParent(schedulerRole.getParent_id());

        respondedSchedulers.add(schedulerInfo);
      }

      HostInfo hostInfo = new HostInfo();
      hostInfo.setId(agentId);
      hostInfo.setSchedulers(respondedSchedulers);
      hostInfo.setVms(respondedVms);

      result.add(hostInfo);
    }

    return generateResourceListResponse(
        Response.Status.OK,
        new ResourceList<>(result));
  }

  private void getAgentResources(final ConcurrentHashMap<String, List<Resource>> responses,
                                 final CountDownLatch done,
                                 final String agent) throws TException, IOException {

    final ServerSet serverSet = serverSetFactory.createHostServerSet(agent);
    final ClientPool<Host.AsyncClient> clientPool = clientPoolFactory.create(serverSet, new ClientPoolOptions()
        .setServiceName("Host")
        .setMaxClients(1)
        .setMaxWaiters(1)
        .setTimeout(30, TimeUnit.SECONDS));
    Host.AsyncClient client = clientProxyFactory.create(clientPool).get();
    client.setTimeout(30000);

    client.get_resources(new GetResourcesRequest(), new AsyncMethodCallback<Host.AsyncClient.get_resources_call>() {
      @Override
      public void onComplete(Host.AsyncClient.get_resources_call response) {
        try {
          responses.put(agent, response.getResult().getResources());
          cleanup(serverSet, clientPool);
          done.countDown();
        } catch (TException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void onError(Exception exception) {
        logger.warn("Couldn't get resources from agent: {}", agent, exception);
        cleanup(serverSet, clientPool);
        done.countDown();
      }

      private void cleanup(ServerSet serverSet, ClientPool<Host.AsyncClient> clientPool) {
        clientPool.close();
        try {
          serverSet.close();
        } catch (IOException e) {
          logger.warn("Unexpected IOException while closing server set: ", e);
        }
      }
    });
  }
}
