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

package com.vmware.photon.controller.deployer.dcp;

import com.vmware.photon.controller.cloudstore.xenon.upgrade.HostTransformationService;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientProvider;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.provider.ListeningExecutorServiceProvider;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactoryProvider;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VibFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VmFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateClusterManagerResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateHostResourceTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateTenantResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.BuildRuntimeConfigurationTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTriggerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.RegisterAuthClientTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.SetDatastoreTagsTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UpgradeAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadImageTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.upgrade.ReflectionTransformationService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddManagementHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BatchCreateManagementWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BuildContainersConfigurationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BulkProvisionHostsWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateContainersWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.RemoveDeploymentWorkflowFactoryService;
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
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactory;
import com.vmware.photon.controller.deployer.deployengine.NsxClientFactoryProvider;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactoryProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ObjectArrays;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;

/**
 * This class implements the Xenon service host object for the deployer service.
 */
@Singleton
public class DeployerXenonServiceHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider,
    DeployerContextProvider,
    DockerProvisionerFactoryProvider,
    ApiClientFactoryProvider,
    ContainersConfigProvider,
    AgentControlClientProvider,
    HostClientProvider,
    ListeningExecutorServiceProvider,
    HttpFileServiceClientFactoryProvider,
    AuthHelperFactoryProvider,
    HealthCheckHelperFactoryProvider,
    ServiceConfiguratorFactoryProvider,
    ZookeeperClientFactoryProvider,
    HostManagementVmAddressValidatorFactoryProvider,
    ClusterManagerFactoryProvider,
    NsxClientFactoryProvider {

  private static final Logger logger = LoggerFactory.getLogger(DeployerXenonServiceHost.class);

  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  public static final Class<?>[] FACTORY_SERVICES_TO_MIGRATE = {
      ContainerFactoryService.class,
      ContainerTemplateFactoryService.class,
      VmFactoryService.class,
  };

  public static final Class<?>[] FACTORY_SERVICES = {

      // Entity Services
      ContainerFactoryService.class,
      ContainerTemplateFactoryService.class,
      VibFactoryService.class,
      VmFactoryService.class,

      // Task Services
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
      TaskSchedulerServiceFactory.class,

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

      // Discovery
      RootNamespaceService.class,

      // Transformation
      HostTransformationService.class,
      ReflectionTransformationService.class,

      // Upgrade
      UpgradeInformationService.class,
  };

  private static final int DEFAULT_TASK_LIMIT = 8;

  protected static final String UPLOAD_VIB_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/vib-uploads";

  private static final Map<String, TaskSchedulerServiceStateBuilder> TASK_SCHEDULERS =
      ImmutableMap.<String, TaskSchedulerServiceStateBuilder>builder()
          .put(UPLOAD_VIB_SCHEDULER_SERVICE,
              new TaskSchedulerServiceStateBuilder(UploadVibTaskService.class, DEFAULT_TASK_LIMIT))
          .build();

  private static final String DEPLOYER_URI = "deployer";

  private BuildInfo buildInfo;

  private final DeployerContext deployerContext;
  private final DockerProvisionerFactory dockerProvisionerFactory;
  private final ApiClientFactory apiClientFactory;
  private final ContainersConfig containersConfig;
  private final AgentControlClientFactory agentControlClientFactory;
  private final HostClientFactory hostClientFactory;
  private final ListeningExecutorService listeningExecutorService;
  private final HttpFileServiceClientFactory httpFileServiceClientFactory;
  private final AuthHelperFactory authHelperFactory;
  private final HealthCheckHelperFactory healthCheckHelperFactory;
  private final ServiceConfiguratorFactory serviceConfiguratorFactory;
  private final ZookeeperClientFactory zookeeperServerSetBuilderFactory;
  private final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
  private final ClusterManagerFactory clusterManagerFactory;
  private final NsxClientFactory nsxClientFactory;

  private final ServerSet cloudStoreServerSet;

  @Inject
  public DeployerXenonServiceHost(
      XenonConfig xenonConfig,
      @CloudStoreServerSet ServerSet cloudStoreServerSet,
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
      AgentControlClientFactory agentControlClientFactory,
      HostClientFactory hostClientFactory,
      HttpFileServiceClientFactory httpFileServiceClientFactory,
      ListeningExecutorService listeningExecutorService,
      ApiClientFactory apiClientFactory,
      DockerProvisionerFactory dockerProvisionerFactory,
      AuthHelperFactory authHelperFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      ZookeeperClientFactory zookeeperServerSetBuilderFactory,
      HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory,
      ClusterManagerFactory clusterManagerFactory,
      NsxClientFactory nsxClientFactory)
      throws Throwable {

    super(xenonConfig);
    this.cloudStoreServerSet = cloudStoreServerSet;
    this.deployerContext = deployerContext;
    this.containersConfig = containersConfig;
    this.agentControlClientFactory = agentControlClientFactory;
    this.hostClientFactory = hostClientFactory;
    this.httpFileServiceClientFactory = httpFileServiceClientFactory;
    this.listeningExecutorService = listeningExecutorService;
    this.apiClientFactory = apiClientFactory;
    this.dockerProvisionerFactory = dockerProvisionerFactory;
    this.authHelperFactory = authHelperFactory;
    this.healthCheckHelperFactory = healthCheckHelperFactory;
    this.serviceConfiguratorFactory = serviceConfiguratorFactory;
    this.zookeeperServerSetBuilderFactory = zookeeperServerSetBuilderFactory;
    this.hostManagementVmAddressValidatorFactory = hostManagementVmAddressValidatorFactory;
    this.clusterManagerFactory = clusterManagerFactory;
    this.nsxClientFactory = nsxClientFactory;
    this.buildInfo = BuildInfo.get(this.getClass());
  }

  /**
   * This method starts the default Xenon core services and deployer Xenon service
   * factories.
   *
   * @return
   * @throws Throwable
   */
  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, getFactoryServices());
    this.addPrivilegedService(CopyStateTaskService.class);

    startTaskSchedulerServices();

    ServiceHostUtils.startService(this, StatusService.class);
    return this;
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
   * This method gets an agent control client from the local agent control client pool.
   *
   * @return
   */
  @Override
  public AgentControlClient getAgentControlClient() {
    return agentControlClientFactory.create();
  }

  /**
   * This method gets a host client from the local host client pool.
   *
   * @return
   */
  @Override
  public HostClient getHostClient() {
    return hostClientFactory.create();
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
   * Getter for Host Management VM Address Validator factory instance.
   *
   * @return
   */
  @Override
  public HostManagementVmAddressValidatorFactory getHostManagementVmAddressValidatorFactory() {
    return hostManagementVmAddressValidatorFactory;
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

  /**
   * Getter for ClusterManager factory instance.
   *
   * @return
   */
  @Override
  public ClusterManagerFactory getClusterManagerFactory() {
    return clusterManagerFactory;
  }

  /**
   * Getter for NsxClient factory instance.
   *
   * @return
   */
  @Override
  public NsxClientFactory getNsxClientFactory() {
    return nsxClientFactory;
  }

  /**
   * Getter for CloudstoreHelper.
   *
   * @return
   */
  public CloudStoreHelper getCloudStoreHelper() {
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(this.cloudStoreServerSet);
    return cloudStoreHelper;
  }

  /**
   * Getter for BuildInfo
   * @return
   */
  public BuildInfo getBuildInfo() {
    return this.buildInfo;
  }

  /**
   * This method returns whether the services started above have come up.
   *
   * @return
   */
  @Override
  public boolean isReady() {
    // schedulers
    for (String selfLink : TASK_SCHEDULERS.keySet()) {
      if (!checkServiceAvailable(selfLink)) {
        return false;
      }
    }

    try {
      return ServiceHostUtils.areServicesReady(
          this, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, FACTORY_SERVICES);
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  @Override
  public Class<?>[] getFactoryServices() {
    return ObjectArrays.concat(
        FACTORY_SERVICES, ClusterManagerFactory.FACTORY_SERVICES,
        Class.class);
  }

  private void startTaskSchedulerServices() {
    registerForServiceAvailability(new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        for (String link : TASK_SCHEDULERS.keySet()) {
          try {
            startTaskSchedulerService(link, TASK_SCHEDULERS.get(link));
          } catch (Exception ex) {
            // This method gets executed on a background thread so since we cannot make return the
            // error to the caller, we swallow the exception here to allow the other the other schedulers
            // to start
            logger.warn("Could not register %s", link, ex);
          }
        }
      }
    }, TaskSchedulerServiceFactory.SELF_LINK);
  }

  private void startTaskSchedulerService(final String selfLink, TaskSchedulerServiceStateBuilder builder)
      throws IllegalAccessException, InstantiationException {
    TaskSchedulerService.State state = builder.build();
    state.documentSelfLink = TaskSchedulerServiceStateBuilder.getSuffixFromSelfLink(selfLink);

    URI uri = UriUtils.buildUri(DeployerXenonServiceHost.this, TaskSchedulerServiceFactory.SELF_LINK, null);
    Operation post = Operation.createPost(uri).setBody(state);
    post.setReferer(UriUtils.buildUri(DeployerXenonServiceHost.this, DEPLOYER_URI));
    sendRequest(post);
  }

  @Override
  public ZookeeperClientFactory getZookeeperServerSetFactoryBuilder() {
    return zookeeperServerSetBuilderFactory;
  }
}
