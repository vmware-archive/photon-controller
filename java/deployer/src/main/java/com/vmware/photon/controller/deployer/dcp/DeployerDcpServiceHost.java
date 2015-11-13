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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.DcpHostInfoProvider;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.ListeningExecutorServiceProvider;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactoryProvider;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.FlavorFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ImageFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ProjectFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ResourceTicketFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.TenantFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VmFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateClusterManagerResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateHostResourceTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.BuildRuntimeConfigurationTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTriggerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateFlavorTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateIsoTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateProjectTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateResourceTicketTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateTenantTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeployAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.GetHostConfigTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.RegisterAuthClientTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.SetDatastoreTagsTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadImageTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForDockerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForServiceTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AllocateResourcesWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BatchCreateManagementWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BuildContainersConfigurationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.BulkProvisionHostsWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateAndValidateContainerWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateContainersWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementVmWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.ProvisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.ProvisionHostWorkflowService;
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
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactoryProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ObjectArrays;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

/**
 * This class implements the DCP service host object for the deployer service.
 */
@Singleton
public class DeployerDcpServiceHost
    extends ServiceHost
    implements DcpHostInfoProvider,
    DeployerContextProvider,
    DockerProvisionerFactoryProvider,
    ApiClientFactoryProvider,
    ContainersConfigProvider,
    HostClientProvider,
    ListeningExecutorServiceProvider,
    HttpFileServiceClientFactoryProvider,
    AuthHelperFactoryProvider,
    HealthCheckHelperFactoryProvider,
    ServiceConfiguratorFactoryProvider,
    ZookeeperClientFactoryProvider,
    HostManagementVmAddressValidatorFactoryProvider,
    ClusterManagerFactoryProvider {

  private static final Logger logger = LoggerFactory.getLogger(DeployerDcpServiceHost.class);

  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  public static final Class[] FACTORY_SERVICES = {
      // Entity Services
      ContainerFactoryService.class,
      ContainerTemplateFactoryService.class,
      FlavorFactoryService.class,
      ImageFactoryService.class,
      ProjectFactoryService.class,
      ResourceTicketFactoryService.class,
      TenantFactoryService.class,
      VmFactoryService.class,

      // Task Services
      ValidateHostTaskFactoryService.class,
      AllocateClusterManagerResourcesTaskFactoryService.class,
      AllocateHostResourceTaskFactoryService.class,
      BuildRuntimeConfigurationTaskFactoryService.class,
      ChangeHostModeTaskFactoryService.class,
      CopyStateTaskFactoryService.class,
      CopyStateTriggerTaskFactoryService.class,
      CreateContainerSpecLayoutTaskFactoryService.class,
      CreateContainerSpecTaskFactoryService.class,
      CreateContainerTaskFactoryService.class,
      CreateFlavorTaskFactoryService.class,
      CreateIsoTaskFactoryService.class,
      CreateManagementVmTaskFactoryService.class,
      CreateProjectTaskFactoryService.class,
      CreateResourceTicketTaskFactoryService.class,
      CreateTenantTaskFactoryService.class,
      CreateVmSpecLayoutTaskFactoryService.class,
      CreateVmSpecTaskFactoryService.class,
      CreateVmTaskFactoryService.class,
      DeleteAgentTaskFactoryService.class,
      DeleteContainerTaskFactoryService.class,
      DeleteVibTaskFactoryService.class,
      DeleteVmTaskFactoryService.class,
      DeployAgentTaskFactoryService.class,
      GetHostConfigTaskFactoryService.class,
      MigrationStatusUpdateTriggerFactoryService.class,
      ProvisionAgentTaskFactoryService.class,
      RegisterAuthClientTaskFactoryService.class,
      SetDatastoreTagsTaskFactoryService.class,
      UploadImageTaskFactoryService.class,
      UploadVibTaskFactoryService.class,
      WaitForDockerTaskFactoryService.class,
      WaitForServiceTaskFactoryService.class,
      TaskSchedulerServiceFactory.class,

      // Workflow services
      AddCloudHostWorkflowFactoryService.class,
      AllocateResourcesWorkflowFactoryService.class,
      BatchCreateManagementWorkflowFactoryService.class,
      BuildContainersConfigurationWorkflowFactoryService.class,
      BulkProvisionHostsWorkflowFactoryService.class,
      CreateAndValidateContainerWorkflowFactoryService.class,
      CreateContainersWorkflowFactoryService.class,
      CreateManagementPlaneLayoutWorkflowFactoryService.class,
      CreateManagementVmWorkflowFactoryService.class,
      DeploymentWorkflowFactoryService.class,
      DeprovisionHostWorkflowFactoryService.class,
      FinalizeDeploymentMigrationWorkflowFactoryService.class,
      InitializeDeploymentMigrationWorkflowFactoryService.class,
      ProvisionHostWorkflowFactoryService.class,
      RemoveDeploymentWorkflowFactoryService.class,
  };

  protected static final String PROVISION_HOST_SCHEDULER_SERVICE =
      TaskSchedulerServiceFactory.SELF_LINK + "/workflow/provision-host";

  private static final Map<String, TaskSchedulerServiceStateBuilder.Config> TASK_SCHEDULERS =
      ImmutableMap.<String, TaskSchedulerServiceStateBuilder.Config>builder()
          .put(PROVISION_HOST_SCHEDULER_SERVICE,
              new TaskSchedulerServiceStateBuilder.Config(ProvisionHostWorkflowService.class, 1))
          .build();

  private static final String DEPLOYER_URI = "deployer";

  private final DeployerContext deployerContext;
  private final DockerProvisionerFactory dockerProvisionerFactory;
  private final ApiClientFactory apiClientFactory;
  private final ContainersConfig containersConfig;
  private final HostClientFactory hostClientFactory;
  private final ListeningExecutorService listeningExecutorService;
  private final HttpFileServiceClientFactory httpFileServiceClientFactory;
  private final AuthHelperFactory authHelperFactory;
  private final HealthCheckHelperFactory healthCheckHelperFactory;
  private final ServiceConfiguratorFactory serviceConfiguratorFactory;
  private final ZookeeperClientFactory zookeeperServerSetBuilderFactory;
  private final HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory;
  private final ClusterManagerFactory clusterManagerFactory;

  private final String registrationAddress;
  private final ServerSet cloudStoreServerSet;

  @Inject
  public DeployerDcpServiceHost(
      @DeployerConfig.Bind String bind,
      @DeployerConfig.Port int port,
      @DeployerConfig.RegistrationAddress String registrationAddress,
      @DcpConfig.StoragePath String storagePath,
      @CloudStoreServerSet ServerSet cloudStoreServerSet,
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
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
      ClusterManagerFactory clusterManagerFactory)
      throws Throwable {

    this(
        bind,
        port,
        registrationAddress,
        storagePath,
        cloudStoreServerSet,
        deployerContext,
        containersConfig,
        hostClientFactory,
        httpFileServiceClientFactory,
        listeningExecutorService,
        apiClientFactory,
        dockerProvisionerFactory,
        authHelperFactory,
        healthCheckHelperFactory,
        serviceConfiguratorFactory,
        null,
        zookeeperServerSetBuilderFactory,
        hostManagementVmAddressValidatorFactory,
        clusterManagerFactory);
  }

  public DeployerDcpServiceHost(
      String bindAddress,
      int port,
      String registrationAddress,
      String storagePath,
      ServerSet cloudStoreServerSet,
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
      HostClientFactory hostClientFactory,
      HttpFileServiceClientFactory httpFileServiceClientFactory,
      ListeningExecutorService listeningExecutorService,
      ApiClientFactory apiClientFactory,
      DockerProvisionerFactory dockerProvisionerFactory,
      AuthHelperFactory authHelperFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      Integer hostId,
      ZookeeperClientFactory zookeeperServerSetBuilderFactory,
      HostManagementVmAddressValidatorFactory hostManagementVmAddressValidatorFactory,
      ClusterManagerFactory clusterManagerFactory)
      throws Throwable {

    this.cloudStoreServerSet = cloudStoreServerSet;
    this.deployerContext = deployerContext;
    this.containersConfig = containersConfig;
    this.hostClientFactory = hostClientFactory;
    this.httpFileServiceClientFactory = httpFileServiceClientFactory;
    this.listeningExecutorService = listeningExecutorService;
    this.apiClientFactory = apiClientFactory;
    this.dockerProvisionerFactory = dockerProvisionerFactory;
    this.authHelperFactory = authHelperFactory;
    this.healthCheckHelperFactory = healthCheckHelperFactory;
    this.serviceConfiguratorFactory = serviceConfiguratorFactory;
    this.registrationAddress = registrationAddress;
    this.zookeeperServerSetBuilderFactory = zookeeperServerSetBuilderFactory;
    this.hostManagementVmAddressValidatorFactory = hostManagementVmAddressValidatorFactory;
    this.clusterManagerFactory = clusterManagerFactory;

    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port + 1;
    arguments.bindAddress = bindAddress;
    arguments.sandbox = Paths.get(storagePath);

    logger.info("Initializing DcpServer on port: {} path: {}", arguments.port, storagePath);

    // Though we can manipulate hostId, we should never set it in production. This is only for
    // testing purpose where a given hostId is easier to debug than random UUID assigned by DCP.
    if (hostId != null) {
      arguments.id = "host-" + hostId;
    }

    this.initialize(arguments);
  }

  /**
   * This method starts the default DCP core services and deployer DCP service
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
    startTaskSchedulerServices();

    return this;
  }

  /**
   * This method gets the containers configurations.
   *
   * @return
   */
  public String getRegistrationAddress() {
    return registrationAddress;
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
   * This method gets the deployer context for the DCP service host.
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
   * Getter for CloudstoreHelper.
   *
   * @return
   */
  public CloudStoreHelper getCloudStoreHelper() {
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(this.cloudStoreServerSet);
    return cloudStoreHelper;
  }

  /**
   * This method returns whether the services started above have come up.
   *
   * @return
   */
  @Override
  public boolean isReady() {
    try {
      return ServiceHostUtils.areServicesReady(this, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, FACTORY_SERVICES);
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  @Override
  public Class[] getFactoryServices() {
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

  private void startTaskSchedulerService(final String selfLink, TaskSchedulerServiceStateBuilder.Config config)
      throws IllegalAccessException, InstantiationException {
    TaskSchedulerServiceStateBuilder builder = new TaskSchedulerServiceStateBuilder();
    TaskSchedulerService.State state = builder.build(config);
    state.documentSelfLink = TaskSchedulerServiceStateBuilder.getSuffixFromSelfLink(selfLink);

    URI uri = UriUtils.buildUri(DeployerDcpServiceHost.this, TaskSchedulerServiceFactory.SELF_LINK, null);
    Operation post = Operation.createPost(uri).setBody(state);
    post.setReferer(UriUtils.buildUri(DeployerDcpServiceHost.this, DEPLOYER_URI));
    sendRequest(post);
  }

  @Override
  public ZookeeperClientFactory getZookeeperServerSetFactoryBuilder() {
    return zookeeperServerSetBuilderFactory;
  }
}
