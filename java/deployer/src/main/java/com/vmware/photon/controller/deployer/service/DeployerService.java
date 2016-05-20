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

package com.vmware.photon.controller.deployer.service;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeEventHandler;
import com.vmware.photon.controller.deployer.DeployerServerSet;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.photon.controller.deployer.gen.CreateHostRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostResponse;
import com.vmware.photon.controller.deployer.gen.CreateHostResult;
import com.vmware.photon.controller.deployer.gen.CreateHostResultCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatus;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusCode;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.CreateHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.photon.controller.deployer.gen.DeleteHostResponse;
import com.vmware.photon.controller.deployer.gen.DeleteHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.DeployResult;
import com.vmware.photon.controller.deployer.gen.DeployResultCode;
import com.vmware.photon.controller.deployer.gen.DeployStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.deployer.gen.Deployment;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResponse;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResult;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatus;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.DeprovisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResult;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeResultCode;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatus;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusCode;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusRequest;
import com.vmware.photon.controller.deployer.gen.EnterMaintenanceModeStatusResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResult;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResult;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeRequest;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResponse;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResult;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeResultCode;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeStatus;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeStatusCode;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeStatusRequest;
import com.vmware.photon.controller.deployer.gen.MaintenanceModeStatusResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeRequest;
import com.vmware.photon.controller.deployer.gen.NormalModeResponse;
import com.vmware.photon.controller.deployer.gen.NormalModeResult;
import com.vmware.photon.controller.deployer.gen.NormalModeResultCode;
import com.vmware.photon.controller.deployer.gen.NormalModeStatus;
import com.vmware.photon.controller.deployer.gen.NormalModeStatusCode;
import com.vmware.photon.controller.deployer.gen.NormalModeStatusRequest;
import com.vmware.photon.controller.deployer.gen.NormalModeStatusResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResponse;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResult;
import com.vmware.photon.controller.deployer.gen.ProvisionHostResultCode;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatus;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusRequest;
import com.vmware.photon.controller.deployer.gen.ProvisionHostStatusResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResult;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusResponse;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClient;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClient;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkFlowServiceClient;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClient;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClient;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.exceptions.InvalidAuthConfigException;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.photon.controller.tracing.gen.TracingInfo;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements the methods required by the deployer Thrift interface.
 */
@Singleton
public class DeployerService implements Deployer.Iface, ServerSet.ChangeListener, ServiceNodeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DeployerService.class);

  private final ServerSet serverSet;
  private final DeployerXenonServiceHost dcpHost;

  private final AddHostWorkflowServiceClientFactory addHostWorkflowServiceClientFactory;
  private final ValidateHostTaskServiceClientFactory validateHostTaskServiceClientFactory;
  private final DeprovisionHostWorkflowServiceClientFactory deprovisionHostClientFactory;
  private final HostServiceClientFactory hostServiceClientFactory;
  private final DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory;
  private final ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory;

  public DeployerService(
      @DeployerServerSet ServerSet serverSet,
      DeployerXenonServiceHost host,
      HostServiceClientFactory hostServiceClientFactory,
      ChangeHostModeTaskServiceClientFactory changeHostModeTaskServiceClientFactory,
      DeploymentWorkflowServiceClientFactory deploymentWorkflowServiceClientFactory,
      AddHostWorkflowServiceClientFactory addHostWorkflowServiceClientFactory,
      ValidateHostTaskServiceClientFactory validateHostTaskServiceClientFactory,
      DeprovisionHostWorkflowServiceClientFactory deprovisionHostClientFactory) {
    this.serverSet = serverSet;
    this.hostServiceClientFactory = hostServiceClientFactory;
    this.changeHostModeTaskServiceClientFactory = changeHostModeTaskServiceClientFactory;
    this.deploymentWorkflowServiceClientFactory = deploymentWorkflowServiceClientFactory;
    this.addHostWorkflowServiceClientFactory = addHostWorkflowServiceClientFactory;
    this.validateHostTaskServiceClientFactory = validateHostTaskServiceClientFactory;
    this.deprovisionHostClientFactory = deprovisionHostClientFactory;
    this.dcpHost = host;
    this.serverSet.addChangeListener(this);
  }

  private static void validateDeployRequest(DeployRequest request)
      throws InvalidAuthConfigException {
    Deployment deployment = request.getDeployment();
    if (deployment == null) {
      throw new IllegalArgumentException("Deployment object is null.");
    }

    if (!deployment.isSetId() || deployment.getId().isEmpty()) {
      throw new IllegalArgumentException("Deployment object 'id' field was not provided.");
    }
  }

  /**
   * This method creates an {@link HostService} entity.
   *
   * @param request
   * @return
   */
  @Override
  public CreateHostResponse create_host(CreateHostRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received create host request [{}]", request);

    CreateHostResult result = new CreateHostResult();
    CreateHostResponse response = new CreateHostResponse();

    try {
      ValidateHostTaskServiceClient client = validateHostTaskServiceClientFactory.getInstance(dcpHost);
      String documentLink = client.validate(request.getHost());
      response.setOperation_id(documentLink);
      result.setCode(CreateHostResultCode.OK);
    } catch (Throwable t) {
      result.setCode(CreateHostResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error during create_host occurred for request [{}]. Error: ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public CreateHostStatusResponse create_host_status(CreateHostStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received create_host_status request [{}]", request);
    CreateHostStatusResponse createHostStatusResponse = new CreateHostStatusResponse();
    CreateHostResult createHostResult = new CreateHostResult();
    CreateHostStatus createHostStatus = new CreateHostStatus();

    try {
      ValidateHostTaskService.TaskState taskState = validateHostTaskServiceClientFactory.getInstance(dcpHost)
          .getValidateHostStatus(request.getOperation_id());

      logger.info("Create host task in stage {} for request [{}].", taskState.stage, request);
      switch (taskState.stage) {
        case CREATED:
        case STARTED:
          createHostStatus.setCode(CreateHostStatusCode.IN_PROGRESS);
          createHostResult.setCode(CreateHostResultCode.OK);
          break;
        case FINISHED:
          createHostStatus.setCode(CreateHostStatusCode.FINISHED);
          createHostResult.setCode(CreateHostResultCode.OK);
          break;
        case FAILED:
          createHostStatus.setCode(CreateHostStatusCode.FAILED);
          createHostStatus.setError(taskState.failure.message);
          createHostResult.setCode(setResultCode(taskState));
          createHostResult.setError(taskState.failure.message);
          break;
        case CANCELLED:
          createHostStatus.setCode(CreateHostStatusCode.CANCELLED);
          createHostResult.setCode(CreateHostResultCode.OK);
          break;
      }

      createHostStatusResponse.setStatus(createHostStatus);

    } catch (ServiceHost.ServiceNotFoundException e) {
      logger.error("Service not found to perform request[{}] ", request, e);
      createHostResult.setCode(CreateHostResultCode.SERVICE_NOT_FOUND);
      createHostResult.setError(e.getMessage());
    } catch (Throwable t) {
      logger.error("Unexpected error occurred during get create host status for request [{}]. Error: ", request, t);
      createHostResult.setCode(CreateHostResultCode.SYSTEM_ERROR);
      createHostResult.setError(t.getMessage());
    }

    createHostStatusResponse.setResult(createHostResult);
    return createHostStatusResponse;
  }

  private CreateHostResultCode setResultCode(ValidateHostTaskService.TaskState taskState) {
    CreateHostResultCode code = CreateHostResultCode.SYSTEM_ERROR;
    if (taskState.resultCode != null) {
      switch (taskState.resultCode) {
        case ExistHostWithSameAddress:
          code = CreateHostResultCode.EXIST_HOST_WITH_SAME_ADDRESS;
          break;
        case InvalidLogin:
          code = CreateHostResultCode.HOST_LOGIN_CREDENTIALS_NOT_VALID;
          break;
        case ManagementVmAddressAlreadyInUse:
          code = CreateHostResultCode.MANAGEMENT_VM_ADDRESS_ALREADY_IN_USE;
          break;
        default:
          break;
      }
    }
    return code;
  }

  @Override
  public DeleteHostResponse delete_host(DeleteHostRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received delete host request [{}]", request);

    DeleteHostResponse response = new DeleteHostResponse();

    try {
      HostServiceClient client = hostServiceClientFactory.getInstance(dcpHost);
      client.delete(request);
      response.setResult(DeleteHostResultCode.OK);
    } catch (ServiceHost.ServiceNotFoundException e) {
      response.setResult(DeleteHostResultCode.HOST_NOT_FOUND);
      response.setError(e.getMessage());
      logger.error("Service not found to perform request [{}], Error: ", request, e);
    } catch (Throwable t) {
      response.setResult(DeleteHostResultCode.SYSTEM_ERROR);
      response.setError(t.getMessage());
      logger.error("Unexpected error during create_host occurred for request [{}]. Error: ", request, t);
    }

    return response;
  }

  /**
   * This method returns the status of the service object.
   *
   * @return
   * @throws TException
   */
  @Override
  public Status get_status() throws TException {
    if (dcpHost.isReady()) {
      Status status = new Status(StatusType.READY);
      return status;
    }
    return new Status(StatusType.INITIALIZING);
  }

  @Override
  public DeployResponse deploy(DeployRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received deploy request [{}]", request);

    DeployResult deployResult = new DeployResult();
    DeployResponse response = new DeployResponse();

    try {
      validateDeployRequest(request);

      DeploymentWorkFlowServiceClient deploymentClient = deploymentWorkflowServiceClientFactory.getInstance(dcpHost);
      HostServiceClient hostClient = hostServiceClientFactory.getInstance(dcpHost);

      Map<String, String> criteria = new HashMap<>();
      criteria.put(HostService.State.FIELD_NAME_USAGE_TAGS, UsageTag.MGMT.name());

      if (deploymentClient.has()) {
        deployResult.setCode(DeployResultCode.EXIST_RUNNING_DEPLOYMENT);
        deployResult.setError("Found running deployment");
      } else if (!hostClient.match(criteria, null)) {
        deployResult.setCode(DeployResultCode.NO_MANAGEMENT_HOST);
        deployResult.setError("Found 0 management host");
      } else {
        String documentLink = deploymentClient.create(request);
        response.setOperation_id(documentLink);
        deployResult.setCode(DeployResultCode.OK);
      }
    } catch (InvalidAuthConfigException e) {
      deployResult.setCode(DeployResultCode.INVALID_OAUTH_CONFIG);
      deployResult.setError(e.getMessage());
      logger.error("Invalid auth config in request [{}], Error: ", request, e);
    } catch (ServiceHost.ServiceNotFoundException e) {
      deployResult.setCode(DeployResultCode.SERVICE_NOT_FOUND);
      deployResult.setError(e.getMessage());
      logger.error("Service not found to perform request [{}], Error: ", request, e);
    } catch (Throwable t) {
      deployResult.setCode(DeployResultCode.SYSTEM_ERROR);
      deployResult.setError(t.getMessage());
      logger.error("Unexpected error during deploy occurred for request [{}]. Error: ", request, t);
    }

    response.setResult(deployResult);
    return response;
  }

  @Override
  public DeployStatusResponse deploy_status(DeployStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received deploy status request [{}]", request);

    DeployStatusResponse response = new DeployStatusResponse();
    DeployResult deployResult = new DeployResult();

    try {
      DeployStatus deployStatus = deploymentWorkflowServiceClientFactory.getInstance(dcpHost)
          .getDeployStatus(request.getOperation_id());

      deployResult.setCode(DeployResultCode.OK);
      response.setStatus(deployStatus);

    } catch (ServiceHost.ServiceNotFoundException e) {
      deployResult.setCode(DeployResultCode.SERVICE_NOT_FOUND);
      deployResult.setError(e.getMessage());
      logger.error("Service not found to perform request [{}]. Error: ", request, e);
    } catch (Throwable t) {
      deployResult.setCode(DeployResultCode.SYSTEM_ERROR);
      deployResult.setError(t.getMessage());
      logger.error("Unexpected error occurred during get deploy status for request [{}]. Error: ", request, t);
    }

    response.setResult(deployResult);
    return response;
  }

  @Override
  public DeprovisionHostStatusResponse deprovision_host_status(DeprovisionHostStatusRequest request)
      throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received deprovision_host request [{}]", request);

    DeprovisionHostStatusResponse response = new DeprovisionHostStatusResponse();
    DeprovisionHostResult result = new DeprovisionHostResult();

    try {
      DeprovisionHostStatus status = deprovisionHostClientFactory.getInstance(dcpHost)
          .getStatus(request.getOperation_id());

      result.setCode(DeprovisionHostResultCode.OK);
      response.setStatus(status);

    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(DeprovisionHostResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      result.setCode(DeprovisionHostResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error occurred during get deprovision_host status for request {} ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public ProvisionHostResponse provision_host(ProvisionHostRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received provision_host request [{}]", request);

    ProvisionHostResult result = new ProvisionHostResult();
    ProvisionHostResponse response = new ProvisionHostResponse();

    try {
      AddHostWorkflowServiceClient addCloudHostClient =
          addHostWorkflowServiceClientFactory.getInstance(dcpHost);
      String taskDocumentLink
          = addCloudHostClient.create(HostServiceFactory.SELF_LINK + "/" + request.getHost_id());
      response.setOperation_id(taskDocumentLink);
      result.setCode(ProvisionHostResultCode.OK);
    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(ProvisionHostResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request [{}], Error: ", request, e);
    } catch (Throwable t) {
      result.setCode(ProvisionHostResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error during provision_host occurred for request [{}]. Error: ", request, t);
    }

    response.setResult(result);

    return response;
  }

  @Override
  public DeprovisionHostResponse deprovision_host(DeprovisionHostRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received deprovision_host request [{}]", request);

    DeprovisionHostResponse response = new DeprovisionHostResponse();
    DeprovisionHostResult result = new DeprovisionHostResult();

    try {
      String taskLink = deprovisionHostClientFactory.getInstance(dcpHost)
          .deprovision(HostServiceFactory.SELF_LINK + "/" + request.getHost_id());
      response.setOperation_id(taskLink);
      result.setCode(DeprovisionHostResultCode.OK);

    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(DeprovisionHostResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      result.setCode(DeprovisionHostResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error occurred during get deprovision_host status for request {} ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public EnterMaintenanceModeResponse set_host_to_enter_maintenance_mode(EnterMaintenanceModeRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received set_host_to_enter_maintenance_mode request [{}]", request);
    EnterMaintenanceModeResponse response = new EnterMaintenanceModeResponse();

    try {
      ChangeHostModeTaskServiceClient client = changeHostModeTaskServiceClientFactory.getInstance(dcpHost);
      String operationId = client.changeHostMode(request.getHostId(), HostMode.ENTERING_MAINTENANCE);
      response.setResult(new EnterMaintenanceModeResult(EnterMaintenanceModeResultCode.OK));
      response.setOperation_id(operationId);
    } catch (Throwable t) {
      response.setResult(new EnterMaintenanceModeResult(EnterMaintenanceModeResultCode.SYSTEM_ERROR));
      logger.error("Unexpected error during set_host_to_enter_maintenance_mode occurred for request [{}]. Error: ",
          request, t);
    }

    return response;
  }

  @Override
  public EnterMaintenanceModeStatusResponse set_host_to_enter_maintenance_mode_status(
      EnterMaintenanceModeStatusRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received enter_maintenance_mode_status request [{}]", request);
    EnterMaintenanceModeStatusResponse enterMaintenanceModeStatusResponse = new EnterMaintenanceModeStatusResponse();
    EnterMaintenanceModeResult enterMaintenanceModeResult = new EnterMaintenanceModeResult();
    EnterMaintenanceModeStatus enterMaintenanceModeStatus = new EnterMaintenanceModeStatus();

    try {
      TaskState changeHostModeTaskState = changeHostModeTaskServiceClientFactory.getInstance(dcpHost)
          .getChangeHostModeStatus(request.getOperation_id());

      logger.info("Change host mode task in stage {} for request [{}].", changeHostModeTaskState.stage, request);
      switch (changeHostModeTaskState.stage) {
        case CREATED:
        case STARTED:
          enterMaintenanceModeStatus.setCode(EnterMaintenanceModeStatusCode.IN_PROGRESS);
          break;
        case FINISHED:
          enterMaintenanceModeStatus.setCode(EnterMaintenanceModeStatusCode.FINISHED);
          break;
        case FAILED:
          enterMaintenanceModeStatus.setCode(EnterMaintenanceModeStatusCode.FAILED);
          enterMaintenanceModeStatus.setError(changeHostModeTaskState.failure.message);
          break;
        case CANCELLED:
          enterMaintenanceModeStatus.setCode(EnterMaintenanceModeStatusCode.FAILED);
          break;
      }
      enterMaintenanceModeStatusResponse.setStatus(enterMaintenanceModeStatus);
      enterMaintenanceModeResult.setCode(EnterMaintenanceModeResultCode.OK);
    } catch (Throwable t) {
      logger.error("Unexpected error occurred during get change host mode status for request [{}]. Error: ",
          request, t);
      enterMaintenanceModeResult.setError(t.getMessage());
      enterMaintenanceModeResult.setCode(EnterMaintenanceModeResultCode.SYSTEM_ERROR);
    }

    enterMaintenanceModeStatusResponse.setResult(enterMaintenanceModeResult);
    return enterMaintenanceModeStatusResponse;
  }

  @Override
  public MaintenanceModeResponse set_host_to_maintenance_mode(MaintenanceModeRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received set_host_to_maintenance_mode request [{}]", request);
    MaintenanceModeResponse response = new MaintenanceModeResponse();

    try {
      ChangeHostModeTaskServiceClient client = changeHostModeTaskServiceClientFactory.getInstance(dcpHost);
      String operationId = client.changeHostMode(request.getHostId(), HostMode.MAINTENANCE);
      response.setResult(new MaintenanceModeResult(MaintenanceModeResultCode.OK));
      response.setOperation_id(operationId);
    } catch (Throwable t) {
      response.setResult(new MaintenanceModeResult(MaintenanceModeResultCode.SYSTEM_ERROR));
      logger.error("Unexpected error during set_host_to_maintenance_mode occurred for request [{}]. Error: ",
          request, t);
    }

    return response;
  }

  @Override
  public MaintenanceModeStatusResponse set_host_to_maintenance_mode_status(MaintenanceModeStatusRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received maintenance_mode_status request [{}]", request);
    MaintenanceModeStatusResponse maintenanceModeStatusResponse = new MaintenanceModeStatusResponse();
    MaintenanceModeResult maintenanceModeResult = new MaintenanceModeResult();
    MaintenanceModeStatus maintenanceModeStatus = new MaintenanceModeStatus();

    try {
      TaskState changeHostModeTaskState = changeHostModeTaskServiceClientFactory.getInstance(dcpHost)
          .getChangeHostModeStatus(request.getOperation_id());

      logger.info("Change host mode task in stage {} for request [{}].", changeHostModeTaskState.stage, request);
      switch (changeHostModeTaskState.stage) {
        case CREATED:
        case STARTED:
          maintenanceModeStatus.setCode(MaintenanceModeStatusCode.IN_PROGRESS);
          break;
        case FINISHED:
          maintenanceModeStatus.setCode(MaintenanceModeStatusCode.FINISHED);
          break;
        case FAILED:
          maintenanceModeStatus.setCode(MaintenanceModeStatusCode.FAILED);
          maintenanceModeStatus.setError(changeHostModeTaskState.failure.message);
          break;
        case CANCELLED:
          maintenanceModeStatus.setCode(MaintenanceModeStatusCode.FAILED);
          break;
      }
      maintenanceModeStatusResponse.setStatus(maintenanceModeStatus);
      maintenanceModeResult.setCode(MaintenanceModeResultCode.OK);
    } catch (Throwable t) {
      logger.error("Unexpected error occurred during get change host mode status for request [{}]. Error: ",
          request, t);
      maintenanceModeResult.setError(t.getMessage());
      maintenanceModeResult.setCode(MaintenanceModeResultCode.SYSTEM_ERROR);
    }

    maintenanceModeStatusResponse.setResult(maintenanceModeResult);
    return maintenanceModeStatusResponse;
  }

  @Override
  public NormalModeResponse set_host_to_normal_mode(NormalModeRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received normal_mode request [{}]", request);
    NormalModeResponse response = new NormalModeResponse();

    try {
      ChangeHostModeTaskServiceClient client = changeHostModeTaskServiceClientFactory.getInstance(dcpHost);
      String operationId = client.changeHostMode(request.getHostId(), HostMode.NORMAL);
      response.setResult(new NormalModeResult(NormalModeResultCode.OK));
      response.setOperation_id(operationId);
    } catch (Throwable t) {
      response.setResult(new NormalModeResult(NormalModeResultCode.SYSTEM_ERROR));
      logger.error("Unexpected error during set_host_to_normal_mode occurred for request [{}]. Error: ",
          request, t);
    }

    return response;
  }

  @Override
  public NormalModeStatusResponse set_host_to_normal_mode_status(NormalModeStatusRequest request) {
    setRequestId(request.getTracing_info());
    logger.info("Received normal_mode_status request [{}]", request);
    NormalModeStatusResponse normalModeStatusResponse = new NormalModeStatusResponse();
    NormalModeResult normalModeResult = new NormalModeResult();
    NormalModeStatus normalModeStatus = new NormalModeStatus();

    try {
      TaskState changeHostModeTaskState = changeHostModeTaskServiceClientFactory.getInstance(dcpHost)
          .getChangeHostModeStatus(request.getOperation_id());

      logger.info("Change host mode task in stage {} for request [{}].", changeHostModeTaskState.stage, request);
      switch (changeHostModeTaskState.stage) {
        case CREATED:
        case STARTED:
          normalModeStatus.setCode(NormalModeStatusCode.IN_PROGRESS);
          break;
        case FINISHED:
          normalModeStatus.setCode(NormalModeStatusCode.FINISHED);
          break;
        case FAILED:
          normalModeStatus.setCode(NormalModeStatusCode.FAILED);
          normalModeStatus.setError(changeHostModeTaskState.failure.message);
          break;
        case CANCELLED:
          normalModeStatus.setCode(NormalModeStatusCode.FAILED);
          break;
      }
      normalModeStatusResponse.setStatus(normalModeStatus);
      normalModeResult.setCode(NormalModeResultCode.OK);
    } catch (Throwable t) {
      logger.error("Unexpected error occurred during get change host mode status for request [{}]. Error: ",
          request, t);
      normalModeResult.setError(t.getMessage());
      normalModeResult.setCode(NormalModeResultCode.SYSTEM_ERROR);
    }

    normalModeStatusResponse.setResult(normalModeResult);
    return normalModeStatusResponse;
  }

  @Override
  public ProvisionHostStatusResponse provision_host_status(ProvisionHostStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received provision_host request [{}]", request);

    ProvisionHostStatusResponse response = new ProvisionHostStatusResponse();
    ProvisionHostResult result = new ProvisionHostResult();

    try {
      ProvisionHostStatus status = addHostWorkflowServiceClientFactory.getInstance(dcpHost)
          .getStatus(request.getOperation_id());

      result.setCode(ProvisionHostResultCode.OK);
      response.setStatus(status);

    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(ProvisionHostResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      result.setCode(ProvisionHostResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error occurred during get provision_host status for request {} ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public InitializeMigrateDeploymentResponse initialize_migrate_deployment(InitializeMigrateDeploymentRequest request)
      throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received initalize migrate deployment request [%s]", request);

    InitializeMigrateDeploymentResult initializeMigrateDeploymentResult = new InitializeMigrateDeploymentResult();
    InitializeMigrateDeploymentResponse response = new InitializeMigrateDeploymentResponse();

    try {
      String documentLink = deploymentWorkflowServiceClientFactory.getInstance(dcpHost).
          initializeMigrateDeployment(request);
      response.setOperation_id(documentLink);
      initializeMigrateDeploymentResult.setCode(InitializeMigrateDeploymentResultCode.OK);
    } catch (ServiceHost.ServiceNotFoundException e) {
      initializeMigrateDeploymentResult.setCode(InitializeMigrateDeploymentResultCode.SERVICE_NOT_FOUND);
      initializeMigrateDeploymentResult.setError(e.getMessage());
      logger.error("Service not found to perform initialize migrate deployment request[{}] ", request, e);
    } catch (Throwable t) {
      initializeMigrateDeploymentResult.setCode(InitializeMigrateDeploymentResultCode.SYSTEM_ERROR);
      initializeMigrateDeploymentResult.setError(t.getMessage());
      logger.error("Unexpected error during initialize migrate deployment occurred for request [{}] Error: ", request,
          t);
    }

    response.setResult(initializeMigrateDeploymentResult);
    return response;
  }

  @Override
  public InitializeMigrateDeploymentStatusResponse initialize_migrate_deployment_status(
      InitializeMigrateDeploymentStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received initialize migrate deployment status request [%s]", request);

    InitializeMigrateDeploymentStatusResponse response = new InitializeMigrateDeploymentStatusResponse();
    InitializeMigrateDeploymentResult result = new InitializeMigrateDeploymentResult();

    try {
      InitializeMigrateDeploymentStatus initializeMigrateDeploymentStatus = deploymentWorkflowServiceClientFactory
          .getInstance(dcpHost)
          .getInitializeMigrateDeploymentStatus(request.getOperation_id());

      result.setCode(InitializeMigrateDeploymentResultCode.OK);
      response.setStatus(initializeMigrateDeploymentStatus);

    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(InitializeMigrateDeploymentResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      result.setCode(InitializeMigrateDeploymentResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error occurred during get remove deploment status for request {} ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public FinalizeMigrateDeploymentResponse finalize_migrate_deployment(FinalizeMigrateDeploymentRequest request)
      throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received finalize migrate deployment request [%s]", request);

    FinalizeMigrateDeploymentResult finalizeMigrateDeploymentResult = new FinalizeMigrateDeploymentResult();
    FinalizeMigrateDeploymentResponse response = new FinalizeMigrateDeploymentResponse();

    try {
      String documentLink = deploymentWorkflowServiceClientFactory.getInstance(dcpHost).
          finalizeMigrateDeployment(request);
      response.setOperation_id(documentLink);
      finalizeMigrateDeploymentResult.setCode(FinalizeMigrateDeploymentResultCode.OK);
    } catch (ServiceHost.ServiceNotFoundException e) {
      finalizeMigrateDeploymentResult.setCode(FinalizeMigrateDeploymentResultCode.SERVICE_NOT_FOUND);
      finalizeMigrateDeploymentResult.setError(e.getMessage());
      logger.error("Service not found to perform finalize migrate deployment request[{}] ", request, e);
    } catch (Throwable t) {
      finalizeMigrateDeploymentResult.setCode(FinalizeMigrateDeploymentResultCode.SYSTEM_ERROR);
      finalizeMigrateDeploymentResult.setError(t.getMessage());
      logger.error("Unexpected error during finalize migrate deployment occurred for request [{}] Error: ", request,
          t);
    }

    response.setResult(finalizeMigrateDeploymentResult);
    return response;
  }

  @Override
  public FinalizeMigrateDeploymentStatusResponse finalize_migrate_deployment_status(
      FinalizeMigrateDeploymentStatusRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received finalize migrate deployment status request [%s]", request);

    FinalizeMigrateDeploymentStatusResponse response = new FinalizeMigrateDeploymentStatusResponse();
    FinalizeMigrateDeploymentResult result = new FinalizeMigrateDeploymentResult();

    try {
      FinalizeMigrateDeploymentStatus finalizeMigrateDeploymentStatus = deploymentWorkflowServiceClientFactory
          .getInstance(dcpHost)
          .getFinalizeMigrateDeploymentStatus(request.getOperation_id());

      result.setCode(FinalizeMigrateDeploymentResultCode.OK);
      response.setStatus(finalizeMigrateDeploymentStatus);

    } catch (ServiceHost.ServiceNotFoundException e) {
      result.setCode(FinalizeMigrateDeploymentResultCode.SERVICE_NOT_FOUND);
      result.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      result.setCode(FinalizeMigrateDeploymentResultCode.SYSTEM_ERROR);
      result.setError(t.getMessage());
      logger.error("Unexpected error occurred during get remove deploment status for request {} ", request, t);
    }

    response.setResult(result);
    return response;
  }

  @Override
  public RemoveDeploymentResponse remove_deployment(RemoveDeploymentRequest request) throws TException {
    setRequestId(request.getTracing_info());
    logger.info("Received remove deployment request [%s]", request);

    RemoveDeploymentResult removeDeploymentResult = new RemoveDeploymentResult();
    RemoveDeploymentResponse response = new RemoveDeploymentResponse();

    try {
      String documentLink = deploymentWorkflowServiceClientFactory.getInstance(dcpHost).remove(request);
      response.setOperation_id(documentLink);
      removeDeploymentResult.setCode(RemoveDeploymentResultCode.OK);
    } catch (ServiceHost.ServiceNotFoundException e) {
      removeDeploymentResult.setCode(RemoveDeploymentResultCode.SERVICE_NOT_FOUND);
      removeDeploymentResult.setError(e.getMessage());
      logger.error("Service not found to perform remove deployment request[{}] ", request, e);
    } catch (Throwable t) {
      removeDeploymentResult.setCode(RemoveDeploymentResultCode.SYSTEM_ERROR);
      removeDeploymentResult.setError(t.getMessage());
      logger.error("Unexpected error during remove deployment occurred for request [{}] Error: ", request, t);
    }

    response.setResult(removeDeploymentResult);
    return response;
  }

  @Override
  public RemoveDeploymentStatusResponse remove_deployment_status(RemoveDeploymentStatusRequest request) throws
      TException {
    setRequestId(request.getTracing_info());
    logger.info("Received remove deployment status request [%s]", request);

    RemoveDeploymentStatusResponse response = new RemoveDeploymentStatusResponse();
    RemoveDeploymentResult removeDeploymentResult = new RemoveDeploymentResult();

    try {
      RemoveDeploymentStatus removeDeploymentStatus = deploymentWorkflowServiceClientFactory.getInstance(dcpHost)
          .getRemoveDeploymentStatus(request.getOperation_id());

      removeDeploymentResult.setCode(RemoveDeploymentResultCode.OK);
      response.setStatus(removeDeploymentStatus);

    } catch (ServiceHost.ServiceNotFoundException e) {
      removeDeploymentResult.setCode(RemoveDeploymentResultCode.SERVICE_NOT_FOUND);
      removeDeploymentResult.setError(e.getMessage());
      logger.error("Service not found to perform request[{}] ", request, e);
    } catch (Throwable t) {
      removeDeploymentResult.setCode(RemoveDeploymentResultCode.SYSTEM_ERROR);
      removeDeploymentResult.setError(t.getMessage());
      logger.error("Unexpected error occurred during get remove deploment status for request {} ", request, t);
    }

    response.setResult(removeDeploymentResult);
    return response;
  }

  /**
   * This method responds to nodes joining the node group.
   */
  @Override
  public void onJoin() {
    logger.info("DeployerService joined.");
  }

  /**
   * This method responds to nodes leaving the node group.
   */
  @Override
  public void onLeave() {
    logger.info("DeployerService left.");
  }

  @VisibleForTesting
  protected Set<InetSocketAddress> getServers() {
    return serverSet.getServers();
  }

  /**
   * This method responds to a new server joining the server set.
   *
   * @param address new server address
   */
  @Override
  public void onServerAdded(InetSocketAddress address) {
    logger.info("Peer server added: " + address);
  }

  /**
   * This method responds to a server leaving the server set.
   *
   * @param address removed server address
   */
  @Override
  public void onServerRemoved(InetSocketAddress address) {
    logger.info("Peer server removed: " + address);
  }

  private void setRequestId(TracingInfo tracingInfo) {
    String requestId = null;
    if (tracingInfo != null) {
      requestId = tracingInfo.getRequest_id();
    }

    if (requestId == null || requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
      logger.warn(String.format("There is no request id passed to Deployer. A new requestId %s is created.",
          requestId));
    }

    LoggingUtils.setRequestId(requestId);
  }
}
