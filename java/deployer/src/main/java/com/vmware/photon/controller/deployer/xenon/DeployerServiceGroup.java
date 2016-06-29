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

package com.vmware.photon.controller.deployer.xenon;

import com.vmware.photon.controller.cloudstore.xenon.upgrade.HostTransformationService;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.common.provider.ListeningExecutorServiceProvider;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.scheduler.RateLimitedWorkQueueFactoryService;
import com.vmware.photon.controller.common.xenon.scheduler.RateLimitedWorkQueueService;
import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactory;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidatorFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactoryProvider;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.VibFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.VmFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateClusterManagerResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateDhcpVmResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateHostResourceTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateTenantResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.BuildRuntimeConfigurationTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTriggerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateDhcpVmTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateVmSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateVmSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.DeleteAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.DeleteContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.DeleteVmTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.RegisterAuthClientTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.SetDatastoreTagsTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.UpgradeAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.UploadImageTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.UploadVibTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.upgrade.ReflectionTransformationService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.AddManagementHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.BatchCreateManagementWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.BuildContainersConfigurationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.BulkProvisionHostsWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.CreateContainersWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.RemoveDeploymentWorkflowFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ObjectArrays;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to initialize the Deployer Xenon services.
 */
public class DeployerServiceGroup
    implements XenonServiceGroup,
    DeployerContextProvider,
    DockerProvisionerFactoryProvider,
    ApiClientFactoryProvider,
    ContainersConfigProvider,
    ListeningExecutorServiceProvider,
    HttpFileServiceClientFactoryProvider,
    AuthHelperFactoryProvider,
    HealthCheckHelperFactoryProvider,
    ServiceConfiguratorFactoryProvider,
    ZookeeperClientFactoryProvider,
    HostManagementVmAddressValidatorFactoryProvider,
    ClusterManagerFactoryProvider {

  private static final Logger logger = LoggerFactory.getLogger(DeployerServiceGroup.class);

  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  private static final String UPLOAD_VIB_WORK_QUEUE_NAME = "vib-uploads";

  @VisibleForTesting
  public static final String UPLOAD_VIB_WORK_QUEUE_SELF_LINK = UriUtils.buildUriPath(
      RateLimitedWorkQueueFactoryService.SELF_LINK, UPLOAD_VIB_WORK_QUEUE_NAME);

  private static final String DEPLOYER_URI = "deployer";

  public static final Class<?>[] FACTORY_SERVICES_TO_MIGRATE = {
      ContainerFactoryService.class,
      ContainerTemplateFactoryService.class,
      VmFactoryService.class,
  };

  public static final Class<?>[] FACTORY_SERVICES = {

      // Infrastructure Services
      RateLimitedWorkQueueFactoryService.class,

      // Entity Services
      ContainerFactoryService.class,
      ContainerTemplateFactoryService.class,
      VibFactoryService.class,
      VmFactoryService.class,

      // Task Services
      AllocateDhcpVmResourcesTaskFactoryService.class,
      AllocateClusterManagerResourcesTaskFactoryService.class,
      AllocateTenantResourcesTaskFactoryService.class,
      AllocateHostResourceTaskFactoryService.class,
      BuildRuntimeConfigurationTaskFactoryService.class,
      ChangeHostModeTaskFactoryService.class,
      ChildTaskAggregatorFactoryService.class,
      CopyStateTaskFactoryService.class,
      CopyStateTriggerTaskFactoryService.class,
      CreateContainerSpecLayoutTaskFactoryService.class,
      CreateContainerSpecTaskFactoryService.class,
      CreateContainerTaskFactoryService.class,
      CreateManagementVmTaskFactoryService.class,
      CreateDhcpVmTaskFactoryService.class,
      CreateVmSpecLayoutTaskFactoryService.class,
      CreateVmSpecTaskFactoryService.class,
      DeleteAgentTaskFactoryService.class,
      DeleteContainerTaskFactoryService.class,
      DeleteVmTaskFactoryService.class,
      MigrationStatusUpdateTriggerFactoryService.class,
      ProvisionHostTaskFactoryService.class,
      RegisterAuthClientTaskFactoryService.class,
      SetDatastoreTagsTaskFactoryService.class,
      UpgradeAgentTaskFactoryService.class,
      UploadImageTaskFactoryService.class,
      UploadVibTaskFactoryService.class,
      ValidateHostTaskFactoryService.class,

      // Workflow services
      AddCloudHostWorkflowFactoryService.class,
      AddManagementHostWorkflowFactoryService.class,
      BatchCreateManagementWorkflowFactoryService.class,
      BuildContainersConfigurationWorkflowFactoryService.class,
      BulkProvisionHostsWorkflowFactoryService.class,
      CreateContainersWorkflowFactoryService.class,
      CreateManagementPlaneLayoutWorkflowFactoryService.class,
      DeploymentWorkflowFactoryService.class,
      DeprovisionHostWorkflowFactoryService.class,
      FinalizeDeploymentMigrationWorkflowFactoryService.class,
      InitializeDeploymentMigrationWorkflowFactoryService.class,
      RemoveDeploymentWorkflowFactoryService.class,

      // Transformation
      HostTransformationService.class,
      ReflectionTransformationService.class,

      // Upgrade
      UpgradeInformationService.class,
  };

  private final DeployerContext deployerContext;
  private final DockerProvisionerFactory dockerProvisionerFactory;
  private final ApiClientFactory apiClientFactory;
  private final ContainersConfig containersConfig;
  private final ListeningExecutorService listeningExecutorService;
  private final HttpFileServiceClientFactory httpFileServiceClientFactory;
  private final AuthHelperFactory authHelperFactory;
  private final HealthCheckHelperFactory healthCheckHelperFactory;
  private final ServiceConfiguratorFactory serviceConfiguratorFactory;
  private  ZookeeperClientFactory zookeeperServerSetBuilderFactory;
  private final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
  private final ClusterManagerFactory clusterManagerFactory;

  private PhotonControllerXenonHost photonControllerXenonHost;

  public DeployerServiceGroup (
      DeployerContext deployerContext,
      DockerProvisionerFactory dockerProvisionerFactory,
      ApiClientFactory apiClientFactory,
      ContainersConfig containersConfig,
      ListeningExecutorService listeningExecutorService,
      HttpFileServiceClientFactory httpFileServiceClientFactory,
      AuthHelperFactory authHelperFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      ZookeeperClientFactory zookeeperServerSetBuilderFactory,
      HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory,
      ClusterManagerFactory clusterManagerFactory) {

    this.deployerContext = deployerContext;
    this.dockerProvisionerFactory = dockerProvisionerFactory;
    this.apiClientFactory = apiClientFactory;
    this.containersConfig = containersConfig;
    this.listeningExecutorService = listeningExecutorService;
    this.httpFileServiceClientFactory = httpFileServiceClientFactory;
    this.authHelperFactory = authHelperFactory;
    this.healthCheckHelperFactory = healthCheckHelperFactory;
    this.serviceConfiguratorFactory = serviceConfiguratorFactory;
    this.zookeeperServerSetBuilderFactory = zookeeperServerSetBuilderFactory;
    this.hostManagementVmAddressValidatorFactory = hostManagementVmAddressValidatorFactory;
    this.clusterManagerFactory = clusterManagerFactory;
  }

  @Override
  public void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost) {
    this.photonControllerXenonHost = photonControllerXenonHost;
  }

  /**
   * This method gets the deployer context for the Xenon service host.
   *
   * @return
   */
  @Override
  public DeployerContext getDeployerContext() {
    return deployerContext;
  }

  /**
   * This method gets the host-wide Docker provisioner factory instance.
   *
   * @return
   */
  @Override
  public DockerProvisionerFactory getDockerProvisionerFactory() {
    return dockerProvisionerFactory;
  }

  /**
   * This method gets the host-wide API client factory.
   *
   * @return
   */
  @Override
  public ApiClientFactory getApiClientFactory() {
    return apiClientFactory;
  }

  /**
   * This method gets the containers configurations.
   *
   * @return
   */
  @Override
  public ContainersConfig getContainersConfig() {
    return containersConfig;
  }

  /**
   * This method gets the host-wide listening executor service instance.
   *
   * @return
   */
  @Override
  public ListeningExecutorService getListeningExecutorService() {
    return listeningExecutorService;
  }

  /**
   * This method gets the host-wide HTTP file service client factory.
   *
   * @return
   */
  @Override
  public HttpFileServiceClientFactory getHttpFileServiceClientFactory() {
    return httpFileServiceClientFactory;
  }

  /**
   * Getter for the authHelper factory instance.
   *
   * @return
   */
  @Override
  public AuthHelperFactory getAuthHelperFactory() {
    return authHelperFactory;
  }

  /**
   * Getter for health Checker factory instance.
   *
   * @return
   */
  @Override
  public HealthCheckHelperFactory getHealthCheckHelperFactory() {
    return healthCheckHelperFactory;
  }

  /**
   * Getter for Service Configurator factory instance.
   *
   * @return
   */
  @Override
  public ServiceConfiguratorFactory getServiceConfiguratorFactory() {
    return serviceConfiguratorFactory;
  }

  @Override
  public ZookeeperClientFactory getZookeeperServerSetFactoryBuilder() {
    return zookeeperServerSetBuilderFactory;
  }

  /**
   * Getter for Host Management VM Address Validator factory instance.
   *
   * @return
   */
  @Override
  public HostManagementVmAddressValidatorFactory getHostManagementVmAddressValidatorFactory() {
    return hostManagementVmAddressValidatorFactory;
  }

  @Override
  public ClusterManagerFactory getClusterManagerFactory() {
    return clusterManagerFactory;
  }

  @Override
  public String getName() {
    return "deployer";
  }

  /**
   * This method starts the default Xenon core services and deployer Xenon service
   * factories.
   *
   * @return
   * @throws Throwable
   */
  @Override
  public void start() throws Throwable {

    ServiceHostUtils.startServices(photonControllerXenonHost, getFactoryServices());
    photonControllerXenonHost.addPrivilegedService(CopyStateTaskService.class);
    startWorkQueueServices();
  }

  /**
   * This method returns whether the services started above have come up.
   *
   * @return
   */
  @Override
  public boolean isReady() {

    if (!photonControllerXenonHost.checkServiceAvailable(UPLOAD_VIB_WORK_QUEUE_SELF_LINK)) {
      return false;
    }

    try {
      return ServiceHostUtils.areServicesReady(
          photonControllerXenonHost, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, FACTORY_SERVICES);
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  public Class<?>[] getFactoryServices() {
    return ObjectArrays.concat(
        FACTORY_SERVICES, ClusterManagerFactory.FACTORY_SERVICES,
        Class.class);
  }

  private void startWorkQueueServices() {

    photonControllerXenonHost.registerForServiceAvailability(
        (o, e) -> {
          if (e != null) {
            logger.error("Failed to start work queue factory service: " + Utils.toString(e));
            return;
          }

          QueryTask.Query pendingTaskServiceQuery = QueryTask.Query.Builder.create()
              .addKindFieldClause(UploadVibTaskService.State.class)
              .addCompositeFieldClause("taskState", "stage",
                  QueryTask.QuerySpecification.toMatchValue(TaskState.TaskStage.CREATED))
              .build();

          RateLimitedWorkQueueService.State startState = new RateLimitedWorkQueueService.State();
          startState.documentSelfLink = UPLOAD_VIB_WORK_QUEUE_NAME;
          startState.pendingTaskServiceQuery = pendingTaskServiceQuery;
          startState.startPatchBody = Utils.toJson(UploadVibTaskService.buildPatch(TaskState.TaskStage.STARTED,
              UploadVibTaskService.TaskState.SubStage.BEGIN_EXECUTION));
          startState.concurrencyLimit = 4;

          photonControllerXenonHost.sendRequest(Operation
              .createPost(photonControllerXenonHost, RateLimitedWorkQueueFactoryService.SELF_LINK)
              .setBody(startState)
              .setReferer(UriUtils.buildUri(photonControllerXenonHost, DEPLOYER_URI)));
        },
        RateLimitedWorkQueueFactoryService.SELF_LINK);
  }


}
