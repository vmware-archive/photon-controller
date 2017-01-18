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

package com.vmware.photon.controller.api.frontend.backends.clients;

import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.frontend.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskService;
import com.vmware.photon.controller.deployer.xenon.util.Pair;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeprovisionHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;

/**
 * Deployer Client Facade that exposes deployer functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class DeployerClient {
  private static final Logger logger = LoggerFactory.getLogger(DeployerClient.class);

  private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

  private PhotonControllerXenonRestClient xenonClient;
  private ApiFeXenonRestClient apiFeXenonRestClient;

  @Inject
  public DeployerClient(PhotonControllerXenonRestClient xenonClient, ApiFeXenonRestClient xenonFeXenonClient)
      throws URISyntaxException {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
    this.apiFeXenonRestClient = xenonFeXenonClient;
    this.apiFeXenonRestClient.start();
  }

  public ValidateHostTaskService.State createHost(HostEntity host)
      throws SpecInvalidException {
    ValidateHostTaskService.State state = new ValidateHostTaskService.State();
    state.hostAddress = host.getAddress();
    state.userName = host.getUsername();
    state.password = host.getPassword();
    state.metadata = host.getMetadata();
    state.usageTags = UsageTagHelper.deserializeToStringSet(host.getUsageTags());
    if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
      String[] allowedDataStores =
          state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES).
              trim().split(COMMA_DELIMITED_REGEX);
      state.dataStores = new HashSet<>();
      Collections.addAll(state.dataStores, allowedDataStores);
    }

    // Persist the database ID of the host to the Xenon entity so we have a unified ID across the system
    state.documentSelfLink = host.getId();

    Operation operation = xenonClient.post(
        ValidateHostTaskFactoryService.SELF_LINK, state);

    return operation.getBody(ValidateHostTaskService.State.class);
  }

  public ValidateHostTaskService.State getHostCreationStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(ValidateHostTaskService.State.class);
  }

  public DeprovisionHostWorkflowService.State getHostDeprovisionStatus(String taskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(taskLink);
    return operation.getBody(DeprovisionHostWorkflowService.State.class);
  }

  public Pair<TaskState, String> getHostProvisionStatus(String taskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(taskLink);
    TaskState taskState;
    ServiceDocument serviceState;
    String hostId;
    serviceState = operation.getBody(AddCloudHostWorkflowService.State.class);
    taskState = ((AddCloudHostWorkflowService.State) serviceState).taskState;
    hostId = ((AddCloudHostWorkflowService.State) serviceState).hostServiceLink;
    return new Pair<>(taskState, ServiceUtils.getIDFromDocumentSelfLink(hostId));
  }

  public DeprovisionHostWorkflowService.State deprovisionHost(String hostServiceLink) {
    DeprovisionHostWorkflowService.State deprovisionHostState = new DeprovisionHostWorkflowService.State();
    deprovisionHostState.hostServiceLink = hostServiceLink;

    Operation operation = xenonClient.post(
        DeprovisionHostWorkflowFactoryService.SELF_LINK, deprovisionHostState);

    return operation.getBody(DeprovisionHostWorkflowService.State.class);
  }

  public AddCloudHostWorkflowService.State provisionCloudHost(String hostServiceLink) {
    AddCloudHostWorkflowService.State addCloudHostState = new AddCloudHostWorkflowService.State();
    addCloudHostState.hostServiceLink = hostServiceLink;
    Operation operation = xenonClient.post(
        AddCloudHostWorkflowFactoryService.SELF_LINK, addCloudHostState);

    return operation.getBody(AddCloudHostWorkflowService.State.class);
  }

  public ChangeHostModeTaskService.State enterSuspendedMode(String hostId) {
    return changeHostMode(hostId, HostMode.ENTERING_MAINTENANCE);
  }

  public ChangeHostModeTaskService.State enterMaintenanceMode(String hostId) {
    return changeHostMode(hostId, HostMode.MAINTENANCE);
  }

  public ChangeHostModeTaskService.State enterNormalMode(String hostId) {
    return changeHostMode(hostId, HostMode.NORMAL);
  }

  private ChangeHostModeTaskService.State changeHostMode(String hostId, HostMode hostMode) {
    ChangeHostModeTaskService.State state = new ChangeHostModeTaskService.State();
    state.hostServiceLink = HostServiceFactory.SELF_LINK + "/" + hostId;
    state.hostMode = hostMode;

    Operation operation = xenonClient.post(
        ChangeHostModeTaskFactoryService.SELF_LINK, state);

    return operation.getBody(ChangeHostModeTaskService.State.class);
  }

  public ChangeHostModeTaskService.State getHostChangeModeStatus(String creationTaskLink)
      throws DocumentNotFoundException {
    Operation operation = xenonClient.get(creationTaskLink);
    return operation.getBody(ChangeHostModeTaskService.State.class);
  }

  public InitializeDeploymentMigrationWorkflowService.State initializeMigrateDeployment
      (String sourceNodeGroupReference, String destinationDeploymentId) {
    InitializeDeploymentMigrationWorkflowService.State state = new
        InitializeDeploymentMigrationWorkflowService.State();
    state.sourceNodeGroupReference = UriUtils.buildUri(sourceNodeGroupReference);
    state.destinationDeploymentId = destinationDeploymentId;

    Operation operation = xenonClient.post(
        InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK, state);

    return operation.getBody(InitializeDeploymentMigrationWorkflowService.State.class);
  }

  public InitializeDeploymentMigrationWorkflowService.State getInitializeMigrateDeploymentStatus
      (String taskLink) throws DocumentNotFoundException {
    Operation operation = xenonClient.get(taskLink);
    return operation.getBody(InitializeDeploymentMigrationWorkflowService.State.class);
  }

  public FinalizeDeploymentMigrationWorkflowService.State finalizeMigrateDeployment
      (String sourceNodeGroupReference, String destinationDeploymentId) {
    InitializeDeploymentMigrationWorkflowService.State state = new
        InitializeDeploymentMigrationWorkflowService.State();
    state.sourceNodeGroupReference = UriUtils.buildUri(sourceNodeGroupReference);
    state.destinationDeploymentId = destinationDeploymentId;

    Operation operation = xenonClient.post(
        FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK, state);

    return operation.getBody(FinalizeDeploymentMigrationWorkflowService.State.class);
  }

  public FinalizeDeploymentMigrationWorkflowService.State getFinalizeMigrateDeploymentStatus
      (String taskLink) throws DocumentNotFoundException {
    Operation operation = xenonClient.get(taskLink);
    return operation.getBody(FinalizeDeploymentMigrationWorkflowService.State.class);
  }
}
