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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.ProvisionRequest;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentConfigurationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;

import com.vmware.photon.controller.resource.gen.ImageDatastore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AgentControl Client Facade that hides the zookeeper/async interactions.
 * Note that this class is not thread safe.
 */
@RpcClient
public class AgentControlClient {

  protected static final ClientPoolOptions CLIENT_POOL_OPTIONS = new ClientPoolOptions()
      .setMaxClients(1)
      .setMaxWaiters(100)
      .setTimeout(30, TimeUnit.SECONDS)
      .setServiceName("AgentControl");
  private static final int DEFAULT_PORT_NUMBER = 8835;
  private static final int MAX_RESERVED_PORT_NUMBER = 1023;

  private static final Logger logger = LoggerFactory.getLogger(AgentControlClient.class);
  private static final long PROVISION_TIMEOUT_MS = 60000;
  private final ClientProxyFactory<AgentControl.AsyncClient> clientProxyFactory;
  private final ClientPoolFactory<AgentControl.AsyncClient> clientPoolFactory;
  private ZookeeperServerSetFactory serverSetFactory;
  /**
   * clientProxy acquires a new client from ClientPool for every thrift call.
   */
  private AgentControl.AsyncClient clientProxy;
  private String hostIp;
  private int port;
  private ClientPool<AgentControl.AsyncClient> clientPool;
  private String agentId;
  private ServerSet serverSet;

  @Inject
  public AgentControlClient(ClientProxyFactory<AgentControl.AsyncClient> clientProxyFactory,
                    ClientPoolFactory<AgentControl.AsyncClient> clientPoolFactory,
                    ZookeeperServerSetFactory serverSetFactory) {
    this.clientProxyFactory = clientProxyFactory;
    this.clientPoolFactory = clientPoolFactory;
    this.serverSetFactory = serverSetFactory;
  }

  public String getHostIp() {
    if (this.getAgentId() != null) {
      return getIpForAgentId();
    }

    return hostIp;
  }

  public void setHostIp(String hostIp) {
    setIpAndPort(hostIp, DEFAULT_PORT_NUMBER);
  }

  public int getPort() {
    return port;
  }

  public void setIpAndPort(String ip, int port) {
    checkNotNull(ip, "IP can not be null");
    checkArgument(port > MAX_RESERVED_PORT_NUMBER,
        "Please set port above %s", MAX_RESERVED_PORT_NUMBER);

    if (ip.equals(this.hostIp) && port == this.port) {
      return;
    }

    this.close();
    this.hostIp = ip;
    this.port = port;
    this.agentId = null;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) throws RpcException {
    if (Objects.equals(this.agentId, agentId)) {
      return;
    }

    this.close();
    this.agentId = agentId;
    this.hostIp = null;
    this.port = 0;
  }

  public void close() {
    clientProxy = null;

    if (clientPool != null) {
      clientPool.close();
      clientPool = null;
    }

    if (serverSet != null) {
      try {
        serverSet.close();
      } catch (IOException e) {
        logger.warn("Exception closing server set", e);
      }
      serverSet = null;
    }
  }

  @VisibleForTesting
  protected void ensureClient() {
    if (clientProxy != null) {
      return;
    }

    close();

    if (this.getAgentId() != null) {
      createClientProxyWithAgentId();
      return;
    }

    createClientProxyWithIpAndPort();
  }

  @VisibleForTesting
  protected void setServerSetFactory(ZookeeperServerSetFactory serverSetFactory) {
    this.serverSetFactory = serverSetFactory;
  }

  @VisibleForTesting
  protected AgentControl.AsyncClient getClientProxy() {
    return clientProxy;
  }

  @VisibleForTesting
  protected void setClientProxy(AgentControl.AsyncClient clientProxy) {
    this.clientProxy = clientProxy;
  }

  /**
   * Get the target of this AgentControlClient.
   *
   * @return If agent id is available, then return agent id; otherwise, return the host ip
   */
  private String getTarget() {
    if (StringUtils.isNotBlank(getAgentId())) {
      return String.format("Agent id: %s, ip: %s", getAgentId(), getHostIp());
    }

    return String.format("Host: %s", getHostIp());
  }

  private String getIpForAgentId() {
    checkNotNull(serverSet, "serverSet is not initialized in ensureClient");
    Set<InetSocketAddress> servers = serverSet.getServers();
    if (servers == null || servers.isEmpty()) {
      logger.warn("There is no host assigned to this agent's serverSet.");
      return null;
    }

    if (servers.size() > 1) {
      throw new IllegalStateException(
          String.format("There is more than one host assigned to this agent's serverSet: %s", servers));
    }

    return servers.iterator().next().getHostString();
  }

  private void createClientProxyWithAgentId() {
    logger.debug("Creating host async client of agentId {}", this.getAgentId());
    checkNotNull(serverSetFactory, "serverSetFactory should not be null to create serverSet");
    serverSet = serverSetFactory.createHostServerSet(agentId);
    clientPool = clientPoolFactory.create(serverSet, CLIENT_POOL_OPTIONS);
    clientProxy = clientProxyFactory.create(clientPool).get();
  }

  private void createClientProxyWithIpAndPort() {
    logger.debug("Creating host async client of hostIp {} and port {}", this.getHostIp(), this.getPort());
    this.clientPool = this.clientPoolFactory.create(
        ImmutableSet.of(new InetSocketAddress(this.getHostIp(), this.getPort())),
        CLIENT_POOL_OPTIONS);
    this.clientProxy = clientProxyFactory.create(clientPool).get();
  }

  /**
   * This method performs an asynchronous Thrift call to provision an agent. On
   * completion, the specified handler is invoked.
   *
   * @param availabilityZone
   * @param dataStoreList
   * @param imageDataStores
   * @param usedForVMs
   * @param networkList
   * @param hostAddress
   * @param hostPort
   * @param chairmanServerList
   * @param memoryOverCommit
   * @param loggingEndpoint
   * @param logLevel
   * @param managementOnly
   * @param hostId
   * @param ntpEndpoint
   * @param handler            Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void provision(
      String availabilityZone,
      List<String> dataStoreList,
      Set<String> imageDataStores,
      boolean usedForVMs,
      List<String> networkList,
      String hostAddress,
      int hostPort,
      List<String> chairmanServerList,
      double memoryOverCommit,
      String loggingEndpoint,
      String logLevel,
      boolean managementOnly,
      String hostId,
      String deploymentId,
      String ntpEndpoint,
      AsyncMethodCallback<AgentControl.AsyncClient.provision_call> handler)
      throws RpcException {
    ensureClient();

    HashSet<ImageDatastore> imageDatastoreSet = new HashSet<>();
    imageDataStores.forEach((imageDatastoreName) -> {
      imageDatastoreSet.add(new ImageDatastore(imageDatastoreName, usedForVMs));
    });

    ProvisionRequest provisionRequest = new ProvisionRequest();
    provisionRequest.setAvailability_zone(availabilityZone);
    provisionRequest.setDatastores(dataStoreList);
    provisionRequest.setNetworks(networkList);
    provisionRequest.setAddress(new ServerAddress(hostAddress, hostPort));
    provisionRequest.setChairman_server(Util.getServerAddressList(chairmanServerList));
    provisionRequest.setMemory_overcommit(memoryOverCommit);
    provisionRequest.setManagement_only(managementOnly);
    provisionRequest.setHost_id(hostId);
    provisionRequest.setDeployment_id(deploymentId);
    provisionRequest.setNtp_endpoint(ntpEndpoint);
    provisionRequest.setImage_datastores(imageDatastoreSet);

    clientProxy.setTimeout(PROVISION_TIMEOUT_MS);
    logger.info("provision target {}, request {}", getTarget(), provisionRequest);

    try {
      clientProxy.provision(provisionRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to provision an agent.
   *
   * @param availabilityZone
   * @param dataStoreList
   * @param imageDataStores
   * @param usedForVMs
   * @param networkList
   * @param hostAddress
   * @param hostPort
   * @param chairmanServerList
   * @param memoryOverCommit
   * @param loggingEndpoint
   * @param logLevel
   * @param managementOnly
   * @param hostId
   * @param ntpEndpoint
   * @return On success, the return code is the ProvisionResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public ProvisionResponse provision(
      String availabilityZone,
      List<String> dataStoreList,
      Set<String> imageDataStores,
      boolean usedForVMs,
      List<String> networkList,
      String hostAddress,
      int hostPort,
      List<String> chairmanServerList,
      double memoryOverCommit,
      String loggingEndpoint,
      String logLevel,
      boolean managementOnly,
      String hostId,
      String deploymentId,
      String ntpEndpoint)
      throws InterruptedException, RpcException {
    SyncHandler<ProvisionResponse, AgentControl.AsyncClient.provision_call> syncHandler = new SyncHandler<>();
    provision(availabilityZone, dataStoreList, imageDataStores, usedForVMs, networkList, hostAddress, hostPort,
        chairmanServerList, memoryOverCommit, loggingEndpoint, logLevel, managementOnly, hostId, deploymentId,
        ntpEndpoint, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkProvisionResponse(syncHandler.getResponse());
  }

  /**
   * Utility class for validating result of response.
   */
  public static class ResponseValidator {

    /**
     * This method validates a ProvisionResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param provisionResponse Supplies a ProvisionResponse object generated by
     *                          a provision call.
     * @return On success, the return value is the ProvisionResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    public static ProvisionResponse checkProvisionResponse(ProvisionResponse provisionResponse)
        throws RpcException {
      logger.info("Checking {}", provisionResponse);
      switch (provisionResponse.getResult()) {
        case OK:
          break;
        case INVALID_CONFIG:
          throw new InvalidAgentConfigurationException(provisionResponse.getError());
        case INVALID_STATE:
          throw new InvalidAgentStateException(provisionResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(provisionResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", provisionResponse.getResult()));
      }

      return provisionResponse;
    }
  }

  /**
   * Class for general utility functions.
   */
  private static class Util {
    private static List<ServerAddress> getServerAddressList(List<String> chairmanServerList)
        throws RpcException {
      List<ServerAddress> result = new ArrayList<>();
      for (String chairmanServer : chairmanServerList) {
        String[] parts = chairmanServer.split(":");
        if (parts.length != 2 || !isValidInteger(parts[1]) || 0 >= Integer.parseInt(parts[1])) {
          logger.error("Invalid chairman entry for agent configuration: {}", chairmanServer);
          throw new InvalidAgentConfigurationException("Invalid chairman entry for agent configuration: "
              + chairmanServer);
        }
        result.add(new ServerAddress(parts[0], Integer.parseInt(parts[1])));
      }
      return result;
    }

    private static boolean isValidInteger(String integerString) {
      boolean result = false;
      try {
        Integer.parseInt(integerString);
        result = true;
      } catch (Exception ex) {
        // Empty
      }
      return result;
    }
  }
}
