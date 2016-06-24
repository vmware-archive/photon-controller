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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskService;
import com.vmware.photon.controller.deployer.xenon.util.Pair;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddManagementHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddManagementHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeprovisionHostWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.deployer.xenon.workflow.RemoveDeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.RemoveDeploymentWorkflowService;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Deployer Client Facade that exposes deployer functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class DeployerClient {
    private static final Logger logger = LoggerFactory.getLogger(DeployerClient.class);

    private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

    private DeployerXenonRestClient xenonClient;
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    public DeployerClient(DeployerXenonRestClient xenonClient, ApiFeXenonRestClient xenonFeXenonClient)
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

        if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
            String[] allowedNetworks =
                    state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS).
                            trim().split(COMMA_DELIMITED_REGEX);
            state.networks = new HashSet<>();
            Collections.addAll(state.networks, allowedNetworks);
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
        if (operation.getBodyRaw().getClass() == AddCloudHostWorkflowService.State.class) {
            serviceState = operation.getBody(AddCloudHostWorkflowService.State.class);
            taskState = ((AddCloudHostWorkflowService.State) serviceState).taskState;
            hostId = ((AddCloudHostWorkflowService.State) serviceState).hostServiceLink;
        } else {
            serviceState = operation.getBody(AddManagementHostWorkflowService.State.class);
            taskState = ((AddManagementHostWorkflowService.State) serviceState).taskState;
            hostId = ((AddManagementHostWorkflowService.State) serviceState).hostServiceLink;
        }
        return new Pair<>(taskState, ServiceUtils.getIDFromDocumentSelfLink(hostId));
    }

    public DeprovisionHostWorkflowService.State deprovisionHost(String hostServiceLink) {
        DeprovisionHostWorkflowService.State deprovisionHostState = new DeprovisionHostWorkflowService.State();
        deprovisionHostState.hostServiceLink = hostServiceLink;

        Operation operation = xenonClient.post(
            DeprovisionHostWorkflowFactoryService.SELF_LINK, deprovisionHostState);

        return operation.getBody(DeprovisionHostWorkflowService.State.class);
    }

    public AddCloudHostWorkflowService.State provisionCloudHost(String hostServiceLink){
        AddCloudHostWorkflowService.State addCloudHostState = new AddCloudHostWorkflowService.State();
        addCloudHostState.hostServiceLink = hostServiceLink;
        Operation operation = xenonClient.post(
            AddCloudHostWorkflowFactoryService.SELF_LINK, addCloudHostState);

        return operation.getBody(AddCloudHostWorkflowService.State.class);
    }

    public AddManagementHostWorkflowService.State provisionManagementHost(String hostServiceLink){
        final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
        List<DeploymentService.State> queryResult = xenonClient.queryDocuments(DeploymentService.State.class,
            termsBuilder.build());

        AddManagementHostWorkflowService.State addMgmtHostState = new AddManagementHostWorkflowService.State();
        addMgmtHostState.hostServiceLink = hostServiceLink;
        addMgmtHostState.isNewDeployment = false;
        addMgmtHostState.deploymentServiceLink = queryResult.get(0).documentSelfLink;
        Operation operation = xenonClient.post(
            AddManagementHostWorkflowFactoryService.SELF_LINK, addMgmtHostState);

        return operation.getBody(AddManagementHostWorkflowService.State.class);
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

    public DeploymentWorkflowService.State deploy(DeploymentEntity deploymentEntity, String desiredState)
        throws ExternalException, InterruptedException {
        DeploymentState deploymentState = null;
        if (desiredState != null) {
            switch (desiredState) {
                case "READY":
                    deploymentState = DeploymentState.READY;
                    break;
                case "PAUSED":
                    deploymentState = DeploymentState.PAUSED;
                    break;
                case "BACKGROUND_PAUSED":
                    deploymentState = DeploymentState.BACKGROUND_PAUSED;
                    break;
                default:
                    throw new RuntimeException(String.format("Unexpected desired state for deployment: %s",
                        desiredState));
            }
        }

        if (isDeploymentAlreadyRunning()) {
            throw new ExternalException("Found running deployment");
        }

        DeploymentWorkflowService.State state = new DeploymentWorkflowService.State();
        state.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + deploymentEntity.getId();
        state.desiredState = deploymentState;

        Operation operation = xenonClient.post(
            DeploymentWorkflowFactoryService.SELF_LINK, state);

        return operation.getBody(DeploymentWorkflowService.State.class);
    }

    public DeploymentWorkflowService.State getDeploymentStatus(String taskLink)
        throws DocumentNotFoundException {
        Operation operation = xenonClient.get(taskLink);
        return operation.getBody(DeploymentWorkflowService.State.class);
    }

    public RemoveDeploymentWorkflowService.State removeDeployment(String deploymentId) {
        RemoveDeploymentWorkflowService.State removeDeploymentState = new RemoveDeploymentWorkflowService.State();
        removeDeploymentState.deploymentId = deploymentId;

        Operation operation = xenonClient.post(
            RemoveDeploymentWorkflowFactoryService.SELF_LINK, removeDeploymentState);

        return operation.getBody(RemoveDeploymentWorkflowService.State.class);
    }

    public RemoveDeploymentWorkflowService.State getRemoveDeploymentStatus(String taskLink)
        throws DocumentNotFoundException {
        Operation operation = xenonClient.get(taskLink);
        return operation.getBody(RemoveDeploymentWorkflowService.State.class);
    }

    public InitializeDeploymentMigrationWorkflowService.State initializeMigrateDeployment
        (String sourceLoadbalancerAddress, String destinationDeploymentId) {
        InitializeDeploymentMigrationWorkflowService.State state = new
            InitializeDeploymentMigrationWorkflowService.State();
        state.destinationDeploymentId = destinationDeploymentId;
        state.sourceLoadBalancerAddress = sourceLoadbalancerAddress;

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
        (String sourceLoadbalancerAddress, String destinationDeploymentId) {
        InitializeDeploymentMigrationWorkflowService.State state = new
            InitializeDeploymentMigrationWorkflowService.State();
        state.destinationDeploymentId = destinationDeploymentId;
        state.sourceLoadBalancerAddress = sourceLoadbalancerAddress;

        Operation operation = xenonClient.post(
            FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK, state);

        return operation.getBody(FinalizeDeploymentMigrationWorkflowService.State.class);
    }

    public FinalizeDeploymentMigrationWorkflowService.State getFinalizeMigrateDeploymentStatus
        (String taskLink) throws DocumentNotFoundException {
        Operation operation = xenonClient.get(taskLink);
        return operation.getBody(FinalizeDeploymentMigrationWorkflowService.State.class);
    }

    public boolean isDeploymentAlreadyRunning() throws ExternalException, InterruptedException {
        final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
        ServiceDocumentQueryResult queryResult = null;
        try {
            queryResult = xenonClient.queryDocuments(
                DeploymentWorkflowService.State.class, termsBuilder.build(), Optional.<Integer>absent(), true, true);
        } catch (DocumentNotFoundException e) {
            return false;
        } catch (BadRequestException be) {
            throw new ExternalException(be);
        } catch (TimeoutException te) {
            throw new ExternalException(te);
        }
        List<DeploymentWorkflowService.State> documents = new ArrayList<>();
        if (queryResult.documentLinks != null) {
            for (String link : queryResult.documentLinks) {
                documents.add(Utils.fromJson(queryResult.documents.get(link), DeploymentWorkflowService.State.class));
            }
        }

        for (DeploymentWorkflowService.State state : documents) {
            if (state.taskState.stage.ordinal() <= DeploymentWorkflowService.TaskState.TaskStage.STARTED.ordinal()) {
                return true;
            }
        }

        return false;
    }
}
