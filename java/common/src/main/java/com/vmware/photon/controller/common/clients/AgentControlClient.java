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
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.agent.gen.PingRequest;
import com.vmware.photon.controller.agent.gen.ProvisionRequest;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.UpgradeRequest;
import com.vmware.photon.controller.agent.gen.UpgradeResponse;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentConfigurationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ImageDatastore;
import com.vmware.photon.controller.stats.plugin.gen.StatsPluginConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Client for Agent's control service.
 * <p>
 * Note that this class is not thread safe, because thrift's TAsyncClient is not thread
 * safe and only allows one method call at a time.
 * <p>
 * Instances of AgentControlClient, HostClient and RootSchedulerClient reuses and
 * shares global TAsyncClientManager and ClientProxyExecutor, so it is fairly
 * cheap to create a new client instance for each use.
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
  private static final long UPGRADE_TIMEOUT_MS = 60000;
  private static final long GET_AGENT_STATUS_TIMEOUT_MS = 60000;
  private static final long PING_TIMEOUT_MS = 5000;
  private final ClientProxyFactory<AgentControl.AsyncClient> clientProxyFactory;
  private final ClientPoolFactory<AgentControl.AsyncClient> clientPoolFactory;
  /**
   * clientProxy acquires a new client from ClientPool for every thrift call.
   */
  private AgentControl.AsyncClient clientProxy;
  private String hostIp;
  private int port;
  private ClientPool<AgentControl.AsyncClient> clientPool;

  @Inject
  public AgentControlClient(ClientProxyFactory<AgentControl.AsyncClient> clientProxyFactory,
                            ClientPoolFactory<AgentControl.AsyncClient> clientPoolFactory) {
    this.clientProxyFactory = clientProxyFactory;
    this.clientPoolFactory = clientPoolFactory;
  }

  public String getHostIp() {
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
  }

  public void close() {
    clientProxy = null;

    if (clientPool != null) {
      clientPool.close();
      clientPool = null;
    }
  }

  @VisibleForTesting
  protected void ensureClient() {
    if (clientProxy != null) {
      return;
    }

    close();

    createClientProxyWithIpAndPort();
  }

  @VisibleForTesting
  protected AgentControl.AsyncClient getClientProxy() {
    return clientProxy;
  }

  @VisibleForTesting
  protected void setClientProxy(AgentControl.AsyncClient clientProxy) {
    this.clientProxy = clientProxy;
  }

  private void createClientProxyWithIpAndPort() {
    logger.debug("Creating host async client of hostIp {} and port {}", this.getHostIp(), this.getPort());
    this.clientPool = this.clientPoolFactory.create(
        ImmutableSet.of(new InetSocketAddress(this.getHostIp(), this.getPort())),
        CLIENT_POOL_OPTIONS);
    this.clientProxy = clientProxyFactory.create(clientPool).get();
  }

  /**
   * This method performs an asynchronous Thrift call to ping the host.
   * On completion, the specified handler is invoked.
   *
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void ping(AsyncMethodCallback<AgentControl.AsyncClient.ping_call> handler) throws RpcException {
    ensureClient();
    PingRequest pingRequest = new PingRequest();
    clientProxy.setTimeout(PING_TIMEOUT_MS);

    try {
      clientProxy.ping(pingRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to ping the host.
   *
   * @throws RpcException
   */
  @RpcMethod
  public void ping() throws InterruptedException, RpcException {
    SyncHandler<Object, AgentControl.AsyncClient.ping_call> syncHandler = new SyncHandler<>();
    ping(syncHandler);
    syncHandler.await();
  }

  /**
   * This method performs an asynchronous Thrift call to provision an agent. On
   * completion, the specified handler is invoked.
   *
   * @param dataStoreList
   * @param imageDataStores
   * @param usedForVMs
   * @param hostAddress
   * @param hostPort
   * @param memoryOverCommit
   * @param loggingEndpoint
   * @param logLevel
   * @param statsPluginConfig
   * @param managementOnly
   * @param hostId
   * @param ntpEndpoint
   * @param handler            Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void provision(
      List<String> dataStoreList,
      Set<String> imageDataStores,
      boolean usedForVMs,
      String hostAddress,
      int hostPort,
      double memoryOverCommit,
      String loggingEndpoint,
      String logLevel,
      StatsPluginConfig statsPluginConfig,
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
    provisionRequest.setDatastores(dataStoreList);
    provisionRequest.setAddress(new ServerAddress(hostAddress, hostPort));
    provisionRequest.setMemory_overcommit(memoryOverCommit);
    provisionRequest.setManagement_only(managementOnly);
    provisionRequest.setHost_id(hostId);
    provisionRequest.setDeployment_id(deploymentId);
    provisionRequest.setNtp_endpoint(ntpEndpoint);
    provisionRequest.setImage_datastores(imageDatastoreSet);
    provisionRequest.setStats_plugin_config(statsPluginConfig);

    clientProxy.setTimeout(PROVISION_TIMEOUT_MS);
    logger.info("provision target: {}, request {}", getHostIp(), provisionRequest);

    try {
      clientProxy.provision(provisionRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to provision an agent.
   *
   * @param dataStoreList
   * @param imageDataStores
   * @param usedForVMs
   * @param hostAddress
   * @param hostPort
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
      List<String> dataStoreList,
      Set<String> imageDataStores,
      boolean usedForVMs,
      String hostAddress,
      int hostPort,
      double memoryOverCommit,
      String loggingEndpoint,
      String logLevel,
      StatsPluginConfig statsPluginConfig,
      boolean managementOnly,
      String hostId,
      String deploymentId,
      String ntpEndpoint)
      throws InterruptedException, RpcException {
    SyncHandler<ProvisionResponse, AgentControl.AsyncClient.provision_call> syncHandler = new SyncHandler<>();
    provision(dataStoreList, imageDataStores, usedForVMs, hostAddress, hostPort,
        memoryOverCommit, loggingEndpoint, logLevel, statsPluginConfig,
        managementOnly, hostId, deploymentId, ntpEndpoint, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkProvisionResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to upgrade an agent. On
   * completion, the specified handler is invoked.
   *
   * @param handler            Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void upgrade(AsyncMethodCallback<AgentControl.AsyncClient.upgrade_call> handler)
      throws RpcException {
    ensureClient();

    UpgradeRequest upgradeRequest = new UpgradeRequest();

    clientProxy.setTimeout(UPGRADE_TIMEOUT_MS);
    logger.info("upgrade target: {}, request {}", getHostIp(), upgradeRequest);

    try {
      clientProxy.upgrade(upgradeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to upgrade an agent.
   *
   * @return On success, the return code is the UpgradeResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public UpgradeResponse upgrade()
      throws InterruptedException, RpcException {
    SyncHandler<UpgradeResponse, AgentControl.AsyncClient.upgrade_call> syncHandler = new SyncHandler<>();
    upgrade(syncHandler);
    syncHandler.await();
    return ResponseValidator.checkUpgradeResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get the status
   * for a agent provisioned host. On completion, the specified handler
   * is invoked.
   *
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getAgentStatus(AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call> handler)
      throws RpcException {
    ensureClient();
    clientProxy.setTimeout(GET_AGENT_STATUS_TIMEOUT_MS);
    logger.info("get_agent_status target {}", getHostIp());

    try {
      clientProxy.get_agent_status(handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to get the status for
   * a agent.
   *
   * @return On success, the return value is the AgentStatusResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public AgentStatusResponse getAgentStatus()
      throws InterruptedException, RpcException {
    SyncHandler<AgentStatusResponse, AgentControl.AsyncClient.get_agent_status_call> syncHandler = new SyncHandler<>();
    getAgentStatus(syncHandler);
    syncHandler.await();
    return ResponseValidator.checkAgentStatusResponse(syncHandler.getResponse(), null);
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

    /**
     * This method validates a UpgradeResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param upgradeResponse Supplies a UpgradeResponse object generated by
     *                          a upgrade call.
     * @return On success, the return value is the UpgradeResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    public static UpgradeResponse checkUpgradeResponse(UpgradeResponse upgradeResponse)
        throws RpcException {
      logger.info("Checking {}", upgradeResponse);
      switch (upgradeResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(upgradeResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", upgradeResponse.getResult()));
      }

      return upgradeResponse;
    }

    /**
     * This method validates a AgentStatusResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param agentStatusResponse Supplies a AgentStatusResponse object generated by
     *                            a getHostConfig call.
     * @return On success, the return value is the AgentStatusResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    public static AgentStatusResponse checkAgentStatusResponse(AgentStatusResponse agentStatusResponse,
                                                               String hostAddress) throws RpcException {
      logger.info("Checking {}", agentStatusResponse);
      switch (agentStatusResponse.getStatus()) {
        case OK:
          break;
        case RESTARTING:
          throw new IllegalStateException("Agent is restarting, host = " + hostAddress);
        case UPGRADING:
          throw new IllegalStateException("Agent is upgrading, host = " + hostAddress);
        default:
          throw new RpcException(String.format("Unknown result: %s", agentStatusResponse.getStatus()));
      }

      return agentStatusResponse;
    }
  }
}
