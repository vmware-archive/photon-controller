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

import com.vmware.photon.controller.common.clients.exceptions.ComponentClientExceptionHandler;
import com.vmware.photon.controller.common.clients.exceptions.HostExistWithSameAddressException;
import com.vmware.photon.controller.common.clients.exceptions.HostNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAuthConfigException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidLoginException;
import com.vmware.photon.controller.common.clients.exceptions.ManagementVmAddressAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.NoManagementHostException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.deployer.gen.CreateHostRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.photon.controller.deployer.gen.DeleteHostResponse;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.DeployStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.deployer.gen.Deployment;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeRequest;
import com.vmware.photon.controller.deployer.gen.NormalModeResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusResponse;
import com.vmware.photon.controller.resource.gen.Host;
import com.vmware.photon.controller.status.gen.Status;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Deployer Client Facade that hides deployer service interactions.
 */

@Singleton
@RpcClient
public class DeployerClient implements StatusProvider {
  private static final Logger logger = LoggerFactory.getLogger(DeployerClient.class);
  private static final long STATUS_CALL_TIMEOUT_MS = 5000; // 5 sec
  private static final long DEFAULT_THRIFT_CALL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);

  private final ClientProxy<Deployer.AsyncClient> proxy;

  @Inject
  public DeployerClient(ClientProxy<Deployer.AsyncClient> proxy) {
    logger.info("Calling DeployerClient constructor: {}", System.identityHashCode(this));
    this.proxy = proxy;
  }

  /**
   * This method performs an asynchronous Thrift call to create a Host. On
   * completion, the specified handler is invoked.
   *
   * @param host
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void createHost(Host host, AsyncMethodCallback<Deployer.AsyncClient.create_host_call> handler)
      throws InterruptedException, RpcException {
    try {
      CreateHostRequest createHostRequest = new CreateHostRequest(host);
      logger.info("create request {}", createHostRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.create_host(createHostRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to create a Host. On error
   * an exception representing the error code is thrown.
   *
   * @param host
   * @throws RpcException
   * @throws InterruptedException
   */
  @RpcMethod
  public CreateHostResponse createHost(Host host)
      throws InterruptedException, RpcException {
    SyncHandler<CreateHostResponse, Deployer.AsyncClient.create_host_call> handler = new SyncHandler<>();
    createHost(host, handler);
    handler.await();
    return ResponseValidator.checkCreateHostResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to delete a Host. On
   * completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void deleteHost(String hostId, AsyncMethodCallback<Deployer.AsyncClient.delete_host_call> handler)
      throws InterruptedException, RpcException {
    try {
      DeleteHostRequest deleteHostRequest = new DeleteHostRequest(hostId);
      logger.info("create request {}", deleteHostRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.delete_host(deleteHostRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to delete a Host. On
   * completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeleteHostResponse deleteHost(String hostId)
      throws InterruptedException, RpcException {
    SyncHandler<DeleteHostResponse, Deployer.AsyncClient.delete_host_call> handler = new SyncHandler<>();
    deleteHost(hostId, handler);
    handler.await();
    return ResponseValidator.checkDeleteHostResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to put a Host to SUSPENDED mode.
   * On completion, the specified handler is invoked.
   * NOTE: What APIFE calls SUSPENDED mode maps to the deployer's ENTER_MAINTENANCE_MODE
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void enterSuspendedMode(String hostId,
                                 AsyncMethodCallback<Deployer.AsyncClient.set_host_to_normal_mode_call> handler)
      throws InterruptedException, RpcException {
    try {
      EnterMaintenanceModeRequest suspendedModeRequest = new EnterMaintenanceModeRequest(hostId);
      logger.info("create request {}", suspendedModeRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.set_host_to_enter_maintenance_mode(suspendedModeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to enter a Host to normal mode from maintenance mode. On
   * completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public EnterMaintenanceModeResponse enterSuspendedMode(String hostId)
      throws InterruptedException, RpcException {
    SyncHandler<EnterMaintenanceModeResponse, Deployer.AsyncClient.set_host_to_enter_maintenance_mode_call> handler =
        new SyncHandler<>();
    enterSuspendedMode(hostId, handler);
    handler.await();
    return ResponseValidator.checkSuspendedModeResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to enter a Host to normal mode from maintenance mode. On
   * completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void enterNormalMode(String hostId,
                              AsyncMethodCallback<Deployer.AsyncClient.set_host_to_normal_mode_call> handler)
      throws InterruptedException, RpcException {
    try {
      NormalModeRequest normalModeRequest = new NormalModeRequest(hostId);
      logger.info("create request {}", normalModeRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.set_host_to_normal_mode(normalModeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to enter a Host to normal mode from maintenance mode. On
   * completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public NormalModeResponse enterNormalMode(String hostId)
      throws InterruptedException, RpcException {
    SyncHandler<NormalModeResponse, Deployer.AsyncClient.set_host_to_normal_mode_call> handler = new SyncHandler<>();
    enterNormalMode(hostId, handler);
    handler.await();
    return ResponseValidator.checkNormalModeResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to put a Host into maintenance mode.
   * On completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void enterMaintenanceMode(String hostId,
                                   AsyncMethodCallback<Deployer.AsyncClient.set_host_to_maintenance_mode_call> handler)
      throws InterruptedException, RpcException {
    try {
      MaintenanceModeRequest maintenanceModeRequest = new MaintenanceModeRequest(hostId);
      logger.info("create request {}", maintenanceModeRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.set_host_to_maintenance_mode(maintenanceModeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to put a Host into maintenance mode.
   * On completion, the specified handler is invoked.
   *
   * @param hostId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public MaintenanceModeResponse enterMaintenanceMode(String hostId)
      throws InterruptedException, RpcException {
    SyncHandler<MaintenanceModeResponse, Deployer.AsyncClient.set_host_to_maintenance_mode_call> handler =
        new SyncHandler<>();
    enterMaintenanceMode(hostId, handler);
    handler.await();

    return ResponseValidator.checkMaintenanceModeResponse(handler.getResponse());
  }

  public Status getStatus() {
    try {
      Deployer.AsyncClient client = proxy.get();

      SyncHandler<Status, Deployer.AsyncClient.get_status_call> handler = new SyncHandler<>();
      client.setTimeout(STATUS_CALL_TIMEOUT_MS);
      client.get_status(handler);
      handler.await();

      return handler.getResponse();
    } catch (Exception ex) {
      logger.error("DeployerClient getStatus call failed with Exception: ", ex);
      return ComponentClientExceptionHandler.handle(ex);
    }
  }

  /**
   * This method performs an asynchronous Thrift call to provision a Host.
   * On completion, the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void provisionHost(
      String id,
      AsyncMethodCallback<Deployer.AsyncClient.provision_host_call> handler)
      throws RpcException {
    try {
      ProvisionHostRequest provisionHostRequest = new ProvisionHostRequest(id);
      logger.info("create request {}", provisionHostRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.provision_host(provisionHostRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to provision a Host.
   * On completion, the specified handler is invoked.
   *
   * @param id
   * @throws RpcException
   * @throws InterruptedException
   */
  @RpcMethod
  public ProvisionHostResponse provisionHost(String id)
      throws RpcException, InterruptedException {
    SyncHandler<ProvisionHostResponse, Deployer.AsyncClient.provision_host_call> handler = new SyncHandler<>();
    provisionHost(id, handler);
    handler.await();
    return ResponseValidator.checkProvisionHostResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to check the status of provisionHost.
   * On completion, the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void provisionHostStatus(
      String id,
      AsyncMethodCallback<Deployer.AsyncClient.provision_host_status_call> handler)
      throws RpcException {
    try {
      ProvisionHostStatusRequest request = new ProvisionHostStatusRequest(id);
      logger.info("create request {}", request);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.provision_host_status(request, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an asynchronous Thrift call to check the status of createHost.
   * On completion, the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void createHostStatus(
      String id,
      AsyncMethodCallback<Deployer.AsyncClient.provision_host_status_call> handler)
      throws RpcException {
    try {
      CreateHostStatusRequest request = new CreateHostStatusRequest(id);
      logger.info("create request {}", request);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.create_host_status(request, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to check the status of provisionHost.
   * On completion, the specified handler is invoked.
   *
   * @param id
   * @throws RpcException
   * @throws InterruptedException
   */
  @RpcMethod
  public ProvisionHostStatusResponse provisionHostStatus(String id)
      throws RpcException, InterruptedException {
    SyncHandler<ProvisionHostStatusResponse, Deployer.AsyncClient.provision_host_status_call> handler
        = new SyncHandler<>();
    provisionHostStatus(id, handler);
    handler.await();
    return ResponseValidator.checkProvisionHostStatusResponse(handler.getResponse());
  }

  /**
   * This method performs asynchronous Thrift call to kick of a initialize migrate deployment. On
   * completion, the specified handler is invoked.
   *
   * @param sourceLoadbalancerAddress
   * @param destinationDeploymentId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public InitializeMigrateDeploymentResponse initializeMigrateDeployment(String sourceLoadbalancerAddress,
                                                                         String destinationDeploymentId)
      throws InterruptedException, RpcException {
    try {
      InitializeMigrateDeploymentRequest initializeMigrateDeploymentRequest = new InitializeMigrateDeploymentRequest
          (destinationDeploymentId, sourceLoadbalancerAddress);
      logger.info("initialize migrate deployment request {}", initializeMigrateDeploymentRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      SyncHandler<InitializeMigrateDeploymentResponse, Deployer.AsyncClient.initialize_migrate_deployment_call> handler
          = new SyncHandler<>();
      client.initialize_migrate_deployment(initializeMigrateDeploymentRequest, handler);
      handler.await();
      return ResponseValidator.checkInitializeMigrateDeploymentResponse(handler.getResponse());
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs a asynchronous Thrift call to retrieve the status of migrationg a deployment. On
   * completion of the call the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void initializeMigrateStatus(String id,
                                      AsyncMethodCallback<Deployer.AsyncClient.initialize_migrate_deployment_call>
                                          handler)
      throws InterruptedException, RpcException {
    try {
      InitializeMigrateDeploymentStatusRequest initializeMigrateDeploymentStatusRequest = new
          InitializeMigrateDeploymentStatusRequest(id);
      logger.info("Initialize migrate deployment status request {}", initializeMigrateDeploymentStatusRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.initialize_migrate_deployment_status(initializeMigrateDeploymentStatusRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to monitor status of a initialize migrate deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public InitializeMigrateDeploymentStatusResponse initializeMigrateStatus(String id)
      throws InterruptedException, RpcException {
    SyncHandler<InitializeMigrateDeploymentStatusResponse, Deployer.AsyncClient.initialize_migrate_deployment_call>
        handler = new SyncHandler<>();
    initializeMigrateStatus(id, handler);
    handler.await();
    return ResponseValidator.checkInitializeMigrateDeploymentStatusResponse(handler.getResponse());
  }

  /**
   * This method performs asynchronous Thrift call to kick of a finalize migrate deployment. On
   * completion, the specified handler is invoked.
   *
   * @param sourceLoadbalancerAddress
   * @param destinationDeploymentId
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public FinalizeMigrateDeploymentResponse finalizeMigrateDeployment(String sourceLoadbalancerAddress,
                                                                         String destinationDeploymentId)
      throws InterruptedException, RpcException {
    try {
      FinalizeMigrateDeploymentRequest finalizeMigrateDeploymentRequest = new FinalizeMigrateDeploymentRequest
          (destinationDeploymentId, sourceLoadbalancerAddress);
      logger.info("finalize migrate deployment request {}", finalizeMigrateDeploymentRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      SyncHandler<FinalizeMigrateDeploymentResponse, Deployer.AsyncClient.finalize_migrate_deployment_call> handler
          = new SyncHandler<>();
      client.finalize_migrate_deployment(finalizeMigrateDeploymentRequest, handler);
      handler.await();
      return ResponseValidator.checkFinalizeMigrateDeploymentResponse(handler.getResponse());
    } catch (TException e) {
      throw new RpcException(e);
    }
  }


  /**
   * This method performs a asynchronous Thrift call to retrieve the status of migrationg a deployment. On
   * completion of the call the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void finalizeMigrateStatus(String id,
                                    AsyncMethodCallback<Deployer.AsyncClient.finalize_migrate_deployment_call> handler)
      throws InterruptedException, RpcException {
    try {
      FinalizeMigrateDeploymentStatusRequest finalizeMigrateDeploymentStatusRequest = new
          FinalizeMigrateDeploymentStatusRequest(id);
      logger.info("Initialize migrate deployment status request {}", finalizeMigrateDeploymentStatusRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.finalize_migrate_deployment_status(finalizeMigrateDeploymentStatusRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to monitor status of migrationg a deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public FinalizeMigrateDeploymentStatusResponse finalizeMigrateStatus(String id)
      throws InterruptedException, RpcException {
    SyncHandler<FinalizeMigrateDeploymentStatusResponse, Deployer.AsyncClient.finalize_migrate_deployment_call>
        handler = new SyncHandler<>();
    finalizeMigrateStatus(id, handler);
    handler.await();
    return ResponseValidator.checkFinalizeMigrateDeploymentStatusResponse(handler.getResponse());
  }

  /**
   * This method performs a synchronous Thrift call to kick of a deployment. On
   * completion, the specified handler is invoked.
   *
   * @param deployment
   * @param handler
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void deploy(Deployment deployment, String desiredState, AsyncMethodCallback<Deployer.AsyncClient.deploy_call>
      handler)
      throws InterruptedException, RpcException {
    try {
      DeployRequest deployRequest = new DeployRequest(deployment);
      deployRequest.setDesired_state(desiredState);
      logger.info("deploy request {}", deployRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.deploy(deployRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to kick of a deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param deployment
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeployResponse deploy(Deployment deployment, String desiredState)
      throws InterruptedException, RpcException {
    SyncHandler<DeployResponse, Deployer.AsyncClient.deploy_call> handler = new SyncHandler<>();
    deploy(deployment, desiredState, handler);
    handler.await();
    return ResponseValidator.checkDeployResponse(handler.getResponse());
  }

  /**
   * This method performs a synchronous Thrift call to retrieve the status of a deployment. On
   * completion of the call the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void deployStatus(String id, AsyncMethodCallback<Deployer.AsyncClient.deploy_status_call> handler)
      throws InterruptedException, RpcException {
    try {
      DeployStatusRequest deployStatusRequest = new DeployStatusRequest(id);
      logger.info("deploy status request {}", deployStatusRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.deploy_status(deployStatusRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to monitor status of a deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeployStatusResponse deployStatus(String id)
      throws InterruptedException, RpcException {
    SyncHandler<DeployStatusResponse, Deployer.AsyncClient.deploy_status_call> handler = new SyncHandler<>();
    deployStatus(id, handler);
    handler.await();
    return ResponseValidator.checkDeployStatusResponse(handler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to deprovision a host.
   *
   * @param hostId
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void deprovisionHost(
      String hostId,
      AsyncMethodCallback<Deployer.AsyncClient.deprovision_host_call> handler)
      throws RpcException {
    try {
      DeprovisionHostRequest request = new DeprovisionHostRequest(hostId);

      logger.info("create request {}", request);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.deprovision_host(request, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs a synchronous Thrift call to deprovision a host.
   *
   * @param hostId
   * @return
   * @throws RpcException
   * @throws InterruptedException
   */
  @RpcMethod
  public DeprovisionHostResponse deprovisionHost(String hostId) throws RpcException, InterruptedException {
    SyncHandler<DeprovisionHostResponse, Deployer.AsyncClient.deprovision_host_call> handler = new SyncHandler<>();
    deprovisionHost(hostId, handler);
    handler.await();
    return ResponseValidator.checkDeprovisionHostResponse(handler.getResponse());
  }


  /**
   * This method performs an asynchronous Thrift call to monitor status of deprovisioning a host. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  private void deprovisionHostStatus(
      String id,
      SyncHandler<DeprovisionHostStatusResponse, Deployer.AsyncClient.deprovision_host_status_call> handler)
      throws RpcException {
    try {
      DeprovisionHostStatusRequest request = new DeprovisionHostStatusRequest(id);
      logger.info("create request {}", request);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.deprovision_host_status(request, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs a synchronous Thrift call to monitor status of deprovisioning a host. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeprovisionHostStatusResponse deprovisionHostStatus(String id)
      throws InterruptedException, RpcException {
    SyncHandler<DeprovisionHostStatusResponse, Deployer.AsyncClient.deprovision_host_status_call> handler
        = new SyncHandler<>();
    deprovisionHostStatus(id, handler);
    handler.await();
    return ResponseValidator.checkDeprovisionHostStatusResponse(handler.getResponse());
  }

  /**
   * This method performs a asynchronous Thrift call to remove a deployment. On
   * completion, the specified handler is invoked.
   *
   * @param deploymentId
   * @param handler
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void removeDeployment(String deploymentId, AsyncMethodCallback<Deployer.AsyncClient.deploy_call> handler)
      throws InterruptedException, RpcException {
    try {
      RemoveDeploymentRequest removeDeploymentRequest = new RemoveDeploymentRequest(deploymentId);
      logger.info("remove deployment {}", deploymentId);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.remove_deployment(removeDeploymentRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to remove a deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param deploymentId
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public RemoveDeploymentResponse removeDeployment(String deploymentId)
      throws InterruptedException, RpcException {
    SyncHandler<RemoveDeploymentResponse, Deployer.AsyncClient.deploy_call> handler = new SyncHandler<>();
    removeDeployment(deploymentId, handler);
    handler.await();
    return ResponseValidator.checkRemoveDeploymentResponse(handler.getResponse());
  }

  /**
   * This method performs a asynchronous Thrift call to retrieve the status of deleteing a deployment. On
   * completion of the call the specified handler is invoked.
   *
   * @param id
   * @param handler
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void removeDeploymentStatus(String id, AsyncMethodCallback<Deployer.AsyncClient.deploy_status_call> handler)
      throws InterruptedException, RpcException {
    try {
      RemoveDeploymentStatusRequest removeDeploymentStatusRequest = new RemoveDeploymentStatusRequest(id);
      logger.info("Delete deploy status request {}", removeDeploymentStatusRequest);

      Deployer.AsyncClient client = proxy.get();
      client.setTimeout(DEFAULT_THRIFT_CALL_TIMEOUT_MS);

      client.remove_deployment_status(removeDeploymentStatusRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }

  /**
   * This method performs an synchronous Thrift call to monitor status of a deployment. On error
   * an exception representing the error code is thrown.
   *
   * @param id
   * @return
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public RemoveDeploymentStatusResponse removeDeploymentStatus(String id)
      throws InterruptedException, RpcException {
    SyncHandler<RemoveDeploymentStatusResponse, Deployer.AsyncClient.remove_deployment_status_call> handler =
        new SyncHandler<>();
    removeDeploymentStatus(id, handler);
    handler.await();
    return ResponseValidator.checkRemoveDeploymentStatusResponse(handler.getResponse());
  }

  @VisibleForTesting
  protected ClientProxy<Deployer.AsyncClient> getProxy() {
    return proxy;
  }

  public CreateHostStatusResponse createHostStatus(String id)
    throws RpcException, InterruptedException {
      SyncHandler<CreateHostStatusResponse, Deployer.AsyncClient.create_host_status_call> handler
          = new SyncHandler<>();
      createHostStatus(id, handler);
      handler.await();
      return ResponseValidator.checkCreateHostStatusResponse(handler.getResponse());
  }

  /**
   * Utility class for validating result of response.
   */
  private static class ResponseValidator {

    /**
     * This method validates the CreateHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static CreateHostResponse checkCreateHostResponse(CreateHostResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the CreateHostStatusResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static CreateHostStatusResponse checkCreateHostStatusResponse(CreateHostStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        case EXIST_HOST_WITH_SAME_ADDRESS:
          throw new HostExistWithSameAddressException(response.getResult().getError());
        case HOST_NOT_FOUND:
          throw new HostNotFoundException(response.getResult().getError());
        case HOST_LOGIN_CREDENTIALS_NOT_VALID:
          throw new InvalidLoginException(response.getResult().getError());
        case MANAGEMENT_VM_ADDRESS_ALREADY_IN_USE:
          throw new ManagementVmAddressAlreadyExistException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the ProvisionHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static ProvisionHostResponse checkProvisionHostResponse(ProvisionHostResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the ProvisionHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static ProvisionHostStatusResponse checkProvisionHostStatusResponse(ProvisionHostStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the DeleteHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static DeleteHostResponse checkDeleteHostResponse(DeleteHostResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case CONCURRENT_HOST_OPERATION:
        case OPERATION_NOT_ALLOWED:
          throw new SystemErrorException(response.getError());
        case HOST_NOT_FOUND:
          throw new HostNotFoundException(response.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the DeleteHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static MaintenanceModeResponse checkMaintenanceModeResponse(MaintenanceModeResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the NormalModeResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static EnterMaintenanceModeResponse checkSuspendedModeResponse(EnterMaintenanceModeResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the NormalModeResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static NormalModeResponse checkNormalModeResponse(NormalModeResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the DeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static DeployResponse checkDeployResponse(DeployResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        case NO_MANAGEMENT_HOST:
          throw new NoManagementHostException(response.getResult().getError());
        case INVALID_OAUTH_CONFIG:
          throw new InvalidAuthConfigException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the DeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static DeployStatusResponse checkDeployStatusResponse(DeployStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the InitializeMigrateDeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static InitializeMigrateDeploymentResponse checkInitializeMigrateDeploymentResponse(
        InitializeMigrateDeploymentResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the InitializeMigrateDeploymentStatusResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static InitializeMigrateDeploymentStatusResponse checkInitializeMigrateDeploymentStatusResponse(
        InitializeMigrateDeploymentStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the FinalizeMigrateDeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static FinalizeMigrateDeploymentResponse checkFinalizeMigrateDeploymentResponse(
        FinalizeMigrateDeploymentResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the FinalizeMigrateDeploymentStatusResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static FinalizeMigrateDeploymentStatusResponse checkFinalizeMigrateDeploymentStatusResponse(
        FinalizeMigrateDeploymentStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the DeprovisionHostResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static DeprovisionHostResponse checkDeprovisionHostResponse(DeprovisionHostResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the DeprovisionHostStatusResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static DeprovisionHostStatusResponse checkDeprovisionHostStatusResponse(
        DeprovisionHostStatusResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }
      return response;
    }

    /**
     * This method validates the RemoveDeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static RemoveDeploymentResponse checkRemoveDeploymentResponse(RemoveDeploymentResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
        case SERVICE_NOT_FOUND:
          throw new SystemErrorException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }

    /**
     * This method validates the RemoveDeploymentResponse object.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    private static RemoveDeploymentStatusResponse checkRemoveDeploymentStatusResponse(RemoveDeploymentStatusResponse
                                                                                          response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult().getCode()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getResult().getError());
        case SERVICE_NOT_FOUND:
          throw new ServiceUnavailableException(response.getResult().getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", response.getResult()));
      }

      return response;
    }
  }
}
