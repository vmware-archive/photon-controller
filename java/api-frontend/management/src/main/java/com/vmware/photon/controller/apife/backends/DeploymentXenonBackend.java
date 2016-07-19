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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.AuthInfo;
import com.vmware.photon.controller.api.model.ClusterConfiguration;
import com.vmware.photon.controller.api.model.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentDeployOperation;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.DhcpConfigurationSpec;
import com.vmware.photon.controller.api.model.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.InitializeMigrationOperation;
import com.vmware.photon.controller.api.model.MigrationStatus;
import com.vmware.photon.controller.api.model.NetworkConfiguration;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.StatsInfo;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.backends.utils.TaskUtils;
import com.vmware.photon.controller.apife.commands.steps.DeploymentCreateStepCmd;
import com.vmware.photon.controller.apife.commands.steps.DeploymentInitializeMigrationStepCmd;
import com.vmware.photon.controller.apife.commands.steps.SystemPauseBackgroundTasksStepCmd;
import com.vmware.photon.controller.apife.commands.steps.SystemPauseStepCmd;
import com.vmware.photon.controller.apife.commands.steps.SystemResumeStepCmd;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ClusterTypeAlreadyConfiguredException;
import com.vmware.photon.controller.apife.exceptions.external.ClusterTypeNotConfiguredException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentAlreadyExistException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageDatastoreSetException;
import com.vmware.photon.controller.apife.exceptions.external.NoManagementHostException;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Deployment Xenon Backend.
 */
@Singleton
public class DeploymentXenonBackend implements DeploymentBackend {

  protected static final String AUTH_ADMIN_USER_NAME = "administrator";

  private static final Logger logger = LoggerFactory.getLogger(DeploymentXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final DeployerClient deployerClient;
  private final TaskBackend taskBackend;
  private final TenantBackend tenantBackend;
  private final TombstoneBackend tombstoneBackend;
  private final HostBackend hostBackend;

  @Inject
  public DeploymentXenonBackend(
      ApiFeXenonRestClient xenonClient,
      DeployerClient deployerClient,
      TaskBackend taskBackend,
      TombstoneBackend tombstoneBackend,
      TenantBackend tenantBackend,
      HostBackend hostBackend) {
    this.xenonClient = xenonClient;
    xenonClient.start();

    this.deployerClient = deployerClient;
    this.taskBackend = taskBackend;
    this.tenantBackend = tenantBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.hostBackend = hostBackend;
  }

  public DeployerClient getDeployerClient() {
    return this.deployerClient;
  }

  @Override
  public TaskEntity prepareCreateDeployment(DeploymentCreateSpec spec) throws ExternalException {
    if (!getAll().isEmpty()) {
      throw new DeploymentAlreadyExistException();
    }

    DeploymentEntity deploymentEntity = createEntity(spec);
    logger.info("created deployment {}", deploymentEntity);

    TaskEntity taskEntity = taskBackend.createCompletedTask(deploymentEntity, Operation.CREATE_DEPLOYMENT);

    return taskEntity;
  }

  @Override
  public TaskEntity prepareDeleteDeployment(String id) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(id);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.DELETE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Delete deployment {}", deploymentEntity);
    tombstone(deploymentEntity);
    TaskEntity taskEntity = this.taskBackend.createCompletedTask(deploymentEntity, Operation.DELETE_DEPLOYMENT);
    return taskEntity;
  }

  @Override
  public TaskEntity prepareInitializeMigrateDeployment(InitializeMigrationOperation  initializeMigrationOperation,
                                                       String destinationDeploymentId) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(destinationDeploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.INITIALIZE_MIGRATE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Initialize migrate  {}", deploymentEntity);
    TaskEntity taskEntity = createInitializeMigrateDeploymentTask(
            initializeMigrationOperation.getSourceLoadBalancerAddress(), deploymentEntity);
    taskEntity.getToBeLockedEntities().add(deploymentEntity);
    return taskEntity;
  }

  @Override
  public TaskEntity prepareFinalizeMigrateDeployment(FinalizeMigrationOperation finalizeMigrationOperation,
                                                     String destinationDeploymentId) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(destinationDeploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.FINALIZE_MIGRATE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Finalize migrate  {}", deploymentEntity);
    TaskEntity taskEntity = createFinalizeMigrateDeploymentTask(
        finalizeMigrationOperation.getSourceLoadBalancerAddress(), deploymentEntity);
    taskEntity.getToBeLockedEntities().add(deploymentEntity);
    return taskEntity;
  }

  @Override
  public TaskEntity prepareDeploy(String deploymentId, DeploymentDeployOperation config) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(deploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.PERFORM_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    TaskEntity taskEntity = createDeployTask(deploymentEntity, config);
    return taskEntity;
  }

  @Override
  public TaskEntity prepareDestroy(String deploymentId) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(deploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.PERFORM_DELETE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Destroy deployment {}", deploymentEntity);
    TaskEntity taskEntity = destroyTask(deploymentEntity);
    return taskEntity;
  }

  @Override
  public TaskEntity updateSecurityGroups(String id, List<String> securityGroups) throws ExternalException {

    DeploymentEntity deploymentEntity = findById(id);

    logger.info("Updating the security groups of deployment {} to {}", id, securityGroups.toString());

    boolean authEnabled = deploymentEntity.getAuthEnabled();
    if (!authEnabled) {
      throw new InvalidAuthConfigException("Auth is not enabled, and security groups cannot be set.");
    }

    DeploymentService.State patch = new DeploymentService.State();
    patch.oAuthSecurityGroups = new ArrayList<>(securityGroups);

    patchDeployment(id, patch);

    deploymentEntity = findById(id);

    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity,
        Operation.UPDATE_DEPLOYMENT_SECURITY_GROUPS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, deploymentEntity,
        Operation.PUSH_DEPLOYMENT_SECURITY_GROUPS);

    List<TenantEntity> tenantEntities = tenantBackend.getAllTenantEntities();
    if (tenantEntities != null && !tenantEntities.isEmpty()) {
      List<BaseEntity> tenantEntitiesToBePushed = new ArrayList();
      tenantEntitiesToBePushed.addAll(tenantEntities);
      taskBackend.getStepBackend().createQueuedStep(taskEntity, tenantEntitiesToBePushed,
          Operation.PUSH_TENANT_SECURITY_GROUPS);
    }

    return taskEntity;
  }

  @Override
  public TaskEntity pauseSystem(String deploymentId) throws ExternalException {
    DeploymentEntity deployment = findById(deploymentId);
    EntityStateValidator.validateOperationState(deployment, deployment.getState(), Operation.PAUSE_SYSTEM,
        DeploymentState.OPERATION_PREREQ_STATE);

    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deployment, Operation.PAUSE_SYSTEM);
    StepEntity stepEntity = this.taskBackend.getStepBackend().createQueuedStep(taskEntity, deployment, Operation
        .PAUSE_SYSTEM);

    stepEntity.createOrUpdateTransientResource(SystemPauseStepCmd.DEPLOYMENT_ID_RESOURCE_KEY, deploymentId);
    return taskEntity;
  }

  @Override
  public TaskEntity pauseBackgroundTasks(String deploymentId) throws ExternalException {
    DeploymentEntity deployment = findById(deploymentId);
    EntityStateValidator.validateOperationState(deployment, deployment.getState(), Operation.PAUSE_BACKGROUND_TASKS,
        DeploymentState.OPERATION_PREREQ_STATE);

    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deployment, Operation.PAUSE_BACKGROUND_TASKS);
    StepEntity stepEntity = this.taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation
        .PAUSE_BACKGROUND_TASKS);
    stepEntity.createOrUpdateTransientResource(SystemPauseBackgroundTasksStepCmd.DEPLOYMENT_ID_RESOURCE_KEY,
        deploymentId);
    return taskEntity;
  }

  @Override
  public TaskEntity resumeSystem(String deploymentId) throws ExternalException {
    DeploymentEntity deployment = findById(deploymentId);
    EntityStateValidator.validateOperationState(deployment, deployment.getState(), Operation.RESUME_SYSTEM,
        DeploymentState.OPERATION_PREREQ_STATE);

    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deployment, Operation.RESUME_SYSTEM);
    StepEntity stepEntity = this.taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESUME_SYSTEM);
    stepEntity.createOrUpdateTransientResource(SystemResumeStepCmd.DEPLOYMENT_ID_RESOURCE_KEY,
        deploymentId);
    return taskEntity;
  }

  @Override
  public Deployment toApiRepresentation(String id) throws DeploymentNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public Deployment toApiRepresentation(DeploymentEntity deploymentEntity) {
    Deployment deployment = new Deployment();

    deployment.setId(deploymentEntity.getId());
    deployment.setState(deploymentEntity.getState());
    deployment.setImageDatastores(deploymentEntity.getImageDatastores());
    deployment.setSyslogEndpoint(deploymentEntity.getSyslogEndpoint());

    StatsInfo stats = new StatsInfo();
    stats.setEnabled(deploymentEntity.getStatsEnabled());
    stats.setStoreEndpoint(deploymentEntity.getStatsStoreEndpoint());
    stats.setStorePort(deploymentEntity.getStatsStorePort());
    stats.setStoreType(deploymentEntity.getStatsStoreType());
    deployment.setStats(stats);

    deployment.setNtpEndpoint(deploymentEntity.getNtpEndpoint());
    deployment.setUseImageDatastoreForVms(deploymentEntity.getUseImageDatastoreForVms());

    AuthInfo authInfo = new AuthInfo();
    authInfo.setEnabled(deploymentEntity.getAuthEnabled());
    authInfo.setEndpoint(deploymentEntity.getOauthEndpoint());
    authInfo.setTenant(deploymentEntity.getOauthTenant());
    authInfo.setSecurityGroups(deploymentEntity.getOauthSecurityGroups());
    deployment.setAuth(authInfo);

    NetworkConfiguration networkConfiguration = new NetworkConfiguration();
    networkConfiguration.setVirtualNetworkEnabled(deploymentEntity.getVirtualNetworkEnabled());
    networkConfiguration.setNetworkManagerAddress(deploymentEntity.getNetworkManagerAddress());
    networkConfiguration.setNetworkManagerUsername(deploymentEntity.getNetworkManagerUsername());
    networkConfiguration.setNetworkManagerPassword(deploymentEntity.getNetworkManagerPassword());
    networkConfiguration.setNetworkZoneId(deploymentEntity.getNetworkZoneId());
    networkConfiguration.setNetworkTopRouterId(deploymentEntity.getNetworkTopRouterId());
    deployment.setNetworkConfiguration(networkConfiguration);

    deployment.setLoadBalancerEnabled(deploymentEntity.getLoadBalancerEnabled());
    deployment.setLoadBalancerAddress(deploymentEntity.getLoadBalancerAddress());
    deployment.setMigrationStatus(generateMigrationStatus(deploymentEntity));

    return deployment;
  }

  private MigrationStatus generateMigrationStatus(DeploymentEntity entity) {
    MigrationStatus status = new MigrationStatus();

    Map<String, Integer> migrationProgress = entity.getMigrationProgress();

    if (migrationProgress == null || migrationProgress.isEmpty()) {
      return status;
    }

    long completedCycles = migrationProgress.values().stream().mapToInt(i -> i).min().orElse(0);
    long dataMigrationCycleProgress = migrationProgress.values().stream().filter(i -> i > completedCycles).count();
    long dataMigrationCycleSize = migrationProgress.size();

    status.setCompletedDataMigrationCycles(completedCycles);
    status.setDataMigrationCycleProgress(dataMigrationCycleProgress);
    status.setDataMigrationCycleSize(dataMigrationCycleSize);
    status.setVibsUploaded(entity.getVibsUploaded());
    status.setVibsUploading(entity.getVibsUploading());

    return status;
  }

  @Override
  public void updateState(DeploymentEntity entity, DeploymentState state)
      throws DeploymentNotFoundException {
    entity.setState(state);

    DeploymentService.State patch = new DeploymentService.State();
    patch.state = state;

    patchDeployment(entity.getId(), patch);
  }

  @Override
  public void tombstone(DeploymentEntity deploymentEntity) {
    tombstoneBackend.create(deploymentEntity.getKind(), deploymentEntity.getId());
    xenonClient.delete(DeploymentServiceFactory.SELF_LINK + "/" + deploymentEntity.getId(),
        new DeploymentService.State());
  }

  @Override
  public List<Deployment> getAll() {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    List<DeploymentService.State> documents =
        xenonClient.queryDocuments(DeploymentService.State.class, termsBuilder.build());
    return toApiRepresentation(documents);
  }

  @Override
  public TaskEntity configureCluster(ClusterConfigurationSpec spec) throws ExternalException {
    if (findClusterConfigurationByType(spec.getType()) != null) {
      throw new ClusterTypeAlreadyConfiguredException(spec.getType());
    }

    ClusterConfigurationService.State state = new ClusterConfigurationService.State();
    state.clusterType = spec.getType();
    state.imageId = spec.getImageId();
    state.documentSelfLink = spec.getType().toString().toLowerCase();

    xenonClient.post(true, ClusterConfigurationServiceFactory.SELF_LINK, state);
    return taskBackend.createCompletedTask(null, Operation.CONFIGURE_CLUSTER);
  }

  @Override
  public TaskEntity deleteClusterConfiguration(ClusterType clusterType) throws ExternalException {
    if (findClusterConfigurationByType(clusterType) == null) {
      throw new ClusterTypeNotConfiguredException(clusterType);
    }

    xenonClient.delete(getClusterConfigurationLink(clusterType), new ClusterConfigurationService.State());
    return taskBackend.createCompletedTask(null, Operation.DELETE_CLUSTER_CONFIGURATION);
  }

  @Override
  public List<ClusterConfiguration> getClusterConfigurations() throws ExternalException {
    List<ClusterConfiguration> clusterConfigurations = new ArrayList<>();
    for (ClusterType clusterType : ClusterType.values()) {
      ClusterConfiguration configuration = findClusterConfigurationByType(clusterType);

      if (configuration != null) {
        clusterConfigurations.add(configuration);
      }
    }

    return clusterConfigurations;
  }

  @Override
  public TaskEntity configureDhcp(DhcpConfigurationSpec spec) throws ExternalException {
    ConfigureDhcpWorkflowDocument state = new ConfigureDhcpWorkflowDocument();
    state.dhcpServerAddresses = spec.getServerAddresses();

    ConfigureDhcpWorkflowDocument finalState = xenonClient.post(ConfigureDhcpWorkflowService.FACTORY_LINK, state)
        .getBody(ConfigureDhcpWorkflowDocument.class);
    return TaskUtils.convertBackEndToMiddleEnd(finalState.taskServiceState);
  }

  @Override
  public DeploymentEntity findById(String id) throws DeploymentNotFoundException {
    return toEntity(getDeploymentById(id));
  }

  @Override
  public TaskEntity prepareUpdateImageDatastores(String id, List<String> imageDatastores) throws ExternalException {
    Set<String> currImageDatastores = getDeploymentById(id).imageDataStoreNames;
    if (!imageDatastores.containsAll(currImageDatastores)) {
      throw new InvalidImageDatastoreSetException("New image datastore list " + imageDatastores.toString() + " is not" +
          " a super set of existing list " + currImageDatastores.toString());
    }

    DeploymentService.State patch = new DeploymentService.State();
    patch.imageDataStoreNames = new HashSet<>(imageDatastores);
    patchDeployment(id, patch);

    DeploymentEntity deploymentEntity = findById(id);
    return taskBackend.createCompletedTask(deploymentEntity, Operation.UPDATE_IMAGE_DATASTORES);
  }

  private DeploymentService.State patchDeployment(String id, DeploymentService.State patch) throws
      DeploymentNotFoundException {
    com.vmware.xenon.common.Operation result;
    try {
      result = xenonClient.patch(DeploymentServiceFactory.SELF_LINK + "/" + id, patch);
    } catch (DocumentNotFoundException e) {
      throw new DeploymentNotFoundException(id);
    }

    return result.getBody(DeploymentService.State.class);
  }

  private DeploymentService.State getDeploymentById(String id) throws DeploymentNotFoundException {
    com.vmware.xenon.common.Operation result;
    try {
      result = xenonClient.get(DeploymentServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DeploymentNotFoundException(id);
    }

    return result.getBody(DeploymentService.State.class);
  }

  private List<Deployment> toApiRepresentation(List<DeploymentService.State> deployments) {
    List<Deployment> deploymentList = new ArrayList<>();

    for (DeploymentService.State deployment : deployments) {
      deploymentList.add(toApiRepresentation(toEntity(deployment)));
    }

    return deploymentList;
  }

  private DeploymentEntity createEntity(DeploymentCreateSpec spec) {

    DeploymentService.State deployment = new DeploymentService.State();

    deployment.state = DeploymentState.NOT_DEPLOYED;
    deployment.imageDataStoreNames = spec.getImageDatastores();
    deployment.imageDataStoreUsedForVMs = spec.isUseImageDatastoreForVms();
    deployment.syslogEndpoint = spec.getSyslogEndpoint();

    StatsInfo stats = spec.getStats();
    deployment.statsEnabled = false;
    if (stats != null) {
      deployment.statsEnabled = stats.getEnabled();
      deployment.statsStoreEndpoint = stats.getStoreEndpoint();
      deployment.statsStorePort = stats.getStorePort();
      deployment.statsStoreType = stats.getStoreType();
    }

    deployment.ntpEndpoint = spec.getNtpEndpoint();
    if (spec.getAuth() != null) {
      deployment.oAuthEnabled = spec.getAuth().getEnabled();

      if (spec.getAuth().getEnabled()) {
        deployment.oAuthTenantName = spec.getAuth().getTenant();
        deployment.oAuthUserName = AUTH_ADMIN_USER_NAME;
        deployment.oAuthPassword = spec.getAuth().getPassword();

        if (spec.getAuth().getSecurityGroups() != null
            && spec.getAuth().getSecurityGroups().size() > 0) {
          deployment.oAuthSecurityGroups = new ArrayList<>(spec.getAuth().getSecurityGroups());
        }
      }
    }

    if (spec.getNetworkConfiguration() != null) {
      deployment.virtualNetworkEnabled = spec.getNetworkConfiguration().getVirtualNetworkEnabled();
      deployment.networkManagerAddress = spec.getNetworkConfiguration().getNetworkManagerAddress();
      deployment.networkManagerUsername = spec.getNetworkConfiguration().getNetworkManagerUsername();
      deployment.networkManagerPassword = spec.getNetworkConfiguration().getNetworkManagerPassword();
      deployment.networkZoneId = spec.getNetworkConfiguration().getNetworkZoneId();
      deployment.networkTopRouterId = spec.getNetworkConfiguration().getNetworkTopRouterId();
    }
    deployment.loadBalancerEnabled = spec.getLoadBalancerEnabled();

    com.vmware.xenon.common.Operation operation =
        xenonClient.post(DeploymentServiceFactory.SELF_LINK, deployment);

    deployment = operation.getBody(DeploymentService.State.class);

    return toEntity(deployment);
  }

  private DeploymentEntity toEntity(DeploymentService.State deployment) {
    DeploymentEntity entity = new DeploymentEntity();

    entity.setId(ServiceUtils.getIDFromDocumentSelfLink(deployment.documentSelfLink));
    entity.setState(deployment.state);
    entity.setImageDatastores(deployment.imageDataStoreNames);
    entity.setUseImageDatastoreForVms(deployment.imageDataStoreUsedForVMs);
    entity.setSyslogEndpoint(deployment.syslogEndpoint);
    entity.setStatsEnabled(deployment.statsEnabled);
    entity.setStatsStoreEndpoint(deployment.statsStoreEndpoint);
    entity.setStatsStorePort(deployment.statsStorePort);
    entity.setStatsStoreType(deployment.statsStoreType);
    entity.setNtpEndpoint(deployment.ntpEndpoint);
    entity.setAuthEnabled(deployment.oAuthEnabled);
    entity.setOauthEndpoint(deployment.oAuthServerAddress);
    entity.setOauthPort(deployment.oAuthServerPort);
    entity.setOauthTenant(deployment.oAuthTenantName);
    entity.setOauthUsername(deployment.oAuthUserName);
    entity.setOauthPassword(deployment.oAuthPassword);
    entity.setOauthSecurityGroups(deployment.oAuthSecurityGroups);
    entity.setVirtualNetworkEnabled(deployment.virtualNetworkEnabled);
    entity.setNetworkManagerAddress(deployment.networkManagerAddress);
    entity.setNetworkManagerUsername(deployment.networkManagerUsername);
    entity.setNetworkManagerPassword(deployment.networkManagerPassword);
    entity.setNetworkZoneId(deployment.networkZoneId);
    entity.setNetworkTopRouterId(deployment.networkTopRouterId);
    entity.setLoadBalancerEnabled(deployment.loadBalancerEnabled);
    entity.setLoadBalancerAddress(deployment.loadBalancerAddress);
    entity.setMigrationProgress(deployment.dataMigrationProgress);
    entity.setVibsUploaded(toLong(deployment.vibsUploaded, 0));
    entity.setVibsUploading(toLong(deployment.vibsUploading, 0));

    return entity;
  }

  private long toLong(Long number, long defaultValue) {
    if (number == null) {
      return defaultValue;
    }
    return number;
  }

  private TaskEntity createDeployTask(DeploymentEntity deploymentEntity, DeploymentDeployOperation config) throws
      ExternalException {
    validateDeploy();
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity, Operation.PERFORM_DEPLOYMENT);

    // create the steps
    StepEntity step = this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_DEPLOYMENT);
    step.createOrUpdateTransientResource(DeploymentCreateStepCmd.DEPLOYMENT_DESIRED_STATE_RESOURCE_KEY,
        config.getDesiredState().name());
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CONTROL_PLANE_HOSTS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CONTROL_PLANE_VMS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CLOUD_HOSTS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CLUSTER_MANAGER);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.MIGRATE_DEPLOYMENT_DATA);

    taskEntity.getToBeLockedEntities().add(deploymentEntity);
    return taskEntity;
  }

  private TaskEntity createInitializeMigrateDeploymentTask(String sourceLoadbalancerAddress,
                                                           DeploymentEntity deploymentEntity) throws ExternalException {
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity,
        Operation.INITIALIZE_MIGRATE_DEPLOYMENT);

    // create the steps
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_INITIALIZE_MIGRATE_DEPLOYMENT);
    initiateStep.createOrUpdateTransientResource(DeploymentInitializeMigrationStepCmd.SOURCE_ADDRESS_RESOURCE_KEY,
        sourceLoadbalancerAddress);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PERFORM_INITIALIZE_MIGRATE_DEPLOYMENT);
    return taskEntity;
  }

  private TaskEntity createFinalizeMigrateDeploymentTask(String sourceLoadbalancerAddress,
                                                         DeploymentEntity deploymentEntity) throws ExternalException {
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity,
        Operation.FINALIZE_MIGRATE_DEPLOYMENT);

    // create the steps
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_FINALIZE_MIGRATE_DEPLOYMENT);
    initiateStep.createOrUpdateTransientResource(DeploymentInitializeMigrationStepCmd.SOURCE_ADDRESS_RESOURCE_KEY,
        sourceLoadbalancerAddress);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PERFORM_FINALIZE_MIGRATE_DEPLOYMENT);
    return taskEntity;
  }

  private TaskEntity destroyTask(DeploymentEntity deploymentEntity) throws ExternalException {
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity, Operation.DESTROY_DEPLOYMENT);

    // create the steps
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_DELETE_DEPLOYMENT);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PERFORM_DELETE_DEPLOYMENT);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.DEPROVISION_HOSTS);
    taskEntity.getToBeLockedEntities().add(deploymentEntity);
    return taskEntity;
  }

  private ClusterConfiguration findClusterConfigurationByType(ClusterType clusterType) throws ExternalException {
    try {
      com.vmware.xenon.common.Operation operation =
          xenonClient.get(getClusterConfigurationLink(clusterType));
      ClusterConfigurationService.State state = operation.getBody(ClusterConfigurationService.State.class);

      ClusterConfiguration configuration = new ClusterConfiguration();
      configuration.setType(state.clusterType);
      configuration.setImageId(state.imageId);

      return configuration;
    } catch (DocumentNotFoundException ex) {
      return null;
    }
  }

  private String getClusterConfigurationLink(ClusterType clusterType) {
    return ClusterConfigurationServiceFactory.SELF_LINK + "/" + clusterType.toString().toLowerCase();
  }

  private void validateDeploy() throws ExternalException {
    if (isNoManagementHost(Optional.absent())){
      throw new NoManagementHostException("No management hosts are found for deployment");
    }
  }

  @VisibleForTesting
  public boolean isNoManagementHost(Optional optional) {
    ResourceList<Host> hostList = null;
    hostList = this.hostBackend.filterByUsage(UsageTag.MGMT, optional);
    if (hostList == null || 0 == hostList.getItems().size()){
      return true;
    }
    return false;
  }
}
