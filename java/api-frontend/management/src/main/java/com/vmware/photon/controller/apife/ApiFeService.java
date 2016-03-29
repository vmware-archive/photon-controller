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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.api.common.filters.LoggingFilter;
import com.vmware.photon.controller.api.common.filters.UrlTrailingSlashFilter;
import com.vmware.photon.controller.api.common.jackson.GuiceModule;
import com.vmware.photon.controller.api.common.providers.ConstraintViolationExceptionMapper;
import com.vmware.photon.controller.api.common.providers.ExternalExceptionMapper;
import com.vmware.photon.controller.api.common.providers.JsonProcessingExceptionMapper;
import com.vmware.photon.controller.api.common.providers.LoggingExceptionMapper;
import com.vmware.photon.controller.api.common.providers.WebApplicationExceptionMapper;
import com.vmware.photon.controller.apife.auth.AuthFilter;
import com.vmware.photon.controller.apife.config.ApiFeConfiguration;
import com.vmware.photon.controller.apife.config.ApiFeStaticConfiguration;
import com.vmware.photon.controller.apife.config.AuthConfig;
import com.vmware.photon.controller.apife.config.ConfigurationUtils;
import com.vmware.photon.controller.apife.filter.PauseFilter;
import com.vmware.photon.controller.apife.resources.AuthResource;
import com.vmware.photon.controller.apife.resources.AvailabilityZoneResource;
import com.vmware.photon.controller.apife.resources.AvailabilityZoneTasksResource;
import com.vmware.photon.controller.apife.resources.AvailabilityZonesResource;
import com.vmware.photon.controller.apife.resources.ClusterResizeResource;
import com.vmware.photon.controller.apife.resources.ClusterResource;
import com.vmware.photon.controller.apife.resources.ClusterVmsResource;
import com.vmware.photon.controller.apife.resources.DatastoreResource;
import com.vmware.photon.controller.apife.resources.DatastoresResource;
import com.vmware.photon.controller.apife.resources.DeploymentAdminGroupsResource;
import com.vmware.photon.controller.apife.resources.DeploymentHostsResource;
import com.vmware.photon.controller.apife.resources.DeploymentResource;
import com.vmware.photon.controller.apife.resources.DeploymentVmsResource;
import com.vmware.photon.controller.apife.resources.DeploymentsResource;
import com.vmware.photon.controller.apife.resources.DiskResource;
import com.vmware.photon.controller.apife.resources.DiskTasksResource;
import com.vmware.photon.controller.apife.resources.FlavorResource;
import com.vmware.photon.controller.apife.resources.FlavorTasksResource;
import com.vmware.photon.controller.apife.resources.FlavorsResource;
import com.vmware.photon.controller.apife.resources.HostResource;
import com.vmware.photon.controller.apife.resources.HostTasksResource;
import com.vmware.photon.controller.apife.resources.HostVmsResource;
import com.vmware.photon.controller.apife.resources.ImageResource;
import com.vmware.photon.controller.apife.resources.ImageTasksResource;
import com.vmware.photon.controller.apife.resources.ImagesResource;
import com.vmware.photon.controller.apife.resources.NetworkPortGroupsSetResource;
import com.vmware.photon.controller.apife.resources.NetworkResource;
import com.vmware.photon.controller.apife.resources.NetworksResource;
import com.vmware.photon.controller.apife.resources.PortGroupResource;
import com.vmware.photon.controller.apife.resources.PortGroupsResource;
import com.vmware.photon.controller.apife.resources.ProjectClustersResource;
import com.vmware.photon.controller.apife.resources.ProjectDisksResource;
import com.vmware.photon.controller.apife.resources.ProjectResource;
import com.vmware.photon.controller.apife.resources.ProjectSecurityGroupsResource;
import com.vmware.photon.controller.apife.resources.ProjectTasksResource;
import com.vmware.photon.controller.apife.resources.ProjectVmsResource;
import com.vmware.photon.controller.apife.resources.ResourceTicketResource;
import com.vmware.photon.controller.apife.resources.ResourceTicketTasksResource;
import com.vmware.photon.controller.apife.resources.StatusResource;
import com.vmware.photon.controller.apife.resources.TaskResource;
import com.vmware.photon.controller.apife.resources.TasksResource;
import com.vmware.photon.controller.apife.resources.TenantProjectsResource;
import com.vmware.photon.controller.apife.resources.TenantResource;
import com.vmware.photon.controller.apife.resources.TenantResourceTicketsResource;
import com.vmware.photon.controller.apife.resources.TenantSecurityGroupsResource;
import com.vmware.photon.controller.apife.resources.TenantTasksResource;
import com.vmware.photon.controller.apife.resources.TenantsResource;
import com.vmware.photon.controller.apife.resources.VmDiskAttachResource;
import com.vmware.photon.controller.apife.resources.VmDiskDetachResource;
import com.vmware.photon.controller.apife.resources.VmIsoAttachResource;
import com.vmware.photon.controller.apife.resources.VmIsoDetachResource;
import com.vmware.photon.controller.apife.resources.VmMetadataSetResource;
import com.vmware.photon.controller.apife.resources.VmMksTicketResource;
import com.vmware.photon.controller.apife.resources.VmNetworksResource;
import com.vmware.photon.controller.apife.resources.VmResource;
import com.vmware.photon.controller.apife.resources.VmTagsResource;
import com.vmware.photon.controller.apife.resources.VmTasksResource;
import com.vmware.photon.controller.common.metrics.GraphiteConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceNode;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceNodeUtils;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.swagger.resources.SwaggerJsonListing;

import com.google.inject.Injector;
import com.hubspot.dropwizard.guice.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import javax.servlet.DispatcherType;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This is the main API Front End Service. When an instance is launched, this is the boot class and the .run
 * method is the method that fires up the server.
 */
public class ApiFeService extends Application<ApiFeStaticConfiguration> {

  public static final String SWAGGER_VERSION = "1.2";
  private static final long retryIntervalMsec = TimeUnit.SECONDS.toMillis(30);
  private static ApiFeConfiguration apiFeConfiguration;
  private Injector injector;
  private ApiFeModule apiModule;
  private ZookeeperModule zookeeperModule;
  private HibernateBundle<ApiFeStaticConfiguration> hibernateBundle;

  public static void main(String[] args) throws Exception {
    setupApiFeConfigurationForServerCommand(args);
    new ApiFeService().run(args);
  }

  /**
   * Best effort to parse configuration file as in `bin/management server /etc/esxcloud/management-api.yml`.
   * It does not work with commands such as
   * `management db migrate /etc/esxcloud/management-api.yml`
   * as in {@link io.dropwizard.migrations.DbCommand}
   */
  private static void setupApiFeConfigurationForServerCommand(String[] args)
      throws IOException, ConfigurationException {
    if (args.length == 2 && "server".equals(args[0])) {
      apiFeConfiguration = ConfigurationUtils.parseConfiguration(args[1]);
    }
  }

  @Override
  public void initialize(Bootstrap<ApiFeStaticConfiguration> bootstrap) {
    bootstrap.addBundle(new AssetsBundle("/assets", "/api/", "index.html"));

    apiModule = new ApiFeModule();
    zookeeperModule = new ZookeeperModule();

    apiModule.setConfiguration(apiFeConfiguration);
    zookeeperModule.setConfig(apiFeConfiguration.getZookeeper());

    zookeeperModule.setConfig(apiFeConfiguration.getZookeeper());

    GuiceBundle<ApiFeStaticConfiguration> guiceBundle = getGuiceBundle();
    bootstrap.addBundle(guiceBundle);

    injector = guiceBundle.getInjector();
  }

  @Override
  public void run(ApiFeStaticConfiguration configuration, Environment environment) throws Exception {
    environment.jersey().register(PauseFilter.class);

    final AuthConfig authConfig = configuration.getAuth();
    if (authConfig.isAuthEnabled()) {
      environment.jersey().register(AuthFilter.class);
    }
    registerResourcesWithSwagger(configuration, environment);

    ValidatorFactory validatorFactory = Validation
        .byDefaultProvider()
        .configure()
        .constraintValidatorFactory(new ConstraintValidatorFactory() {
          @Override
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            return injector.getInstance(key);
          }

          @Override
          public void releaseInstance(ConstraintValidator<?, ?> constraintValidator) {
            // do nothing
          }
        }).buildValidatorFactory();
    environment.setValidator(validatorFactory.getValidator());

    environment.getObjectMapper().registerModule(injector.getInstance(GuiceModule.class));
    environment.jersey().register(injector.getInstance(ExternalExceptionMapper.class));
    environment.jersey().register(injector.getInstance(ConstraintViolationExceptionMapper.class));
    environment.jersey().register(injector.getInstance(JsonProcessingExceptionMapper.class));
    environment.jersey().register(injector.getInstance(LoggingExceptionMapper.class));
    environment.jersey().register(injector.getInstance(WebApplicationExceptionMapper.class));

    environment.servlets().addFilter("LoggingFilter", injector.getInstance(LoggingFilter.class))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    environment.servlets().addFilter("UrlTrailingSlashFilter", injector.getInstance(UrlTrailingSlashFilter.class))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/api");

    GraphiteConfig graphite = configuration.getGraphite();
    if (graphite != null) {
      graphite.enable();
    }

    HttpConnectorFactory httpConnectorFactory = (HttpConnectorFactory) ((DefaultServerFactory) configuration
        .getServerFactory()).getApplicationConnectors().get(0);
    registerWithZookeeper(
        injector.getInstance(ServiceNodeFactory.class),
        configuration.getRegistrationAddress(),
        httpConnectorFactory.getPort());
  }

  private void registerWithZookeeper(ServiceNodeFactory serviceNodeFactory, String registrationIpAddress,
                                     int port) {
    InetSocketAddress registrationSocketAddress = new InetSocketAddress(registrationIpAddress, port);
    ServiceNode serviceNode = serviceNodeFactory.createSimple("apife", registrationSocketAddress);
    ServiceNodeUtils.joinService(serviceNode, retryIntervalMsec);
  }

  private void registerResourcesWithSwagger(ApiFeConfiguration configuration, Environment environment) {
    List<Class<?>> resources = new ArrayList<>();
    resources.add(AuthResource.class);
    resources.add(AvailabilityZoneResource.class);
    resources.add(AvailabilityZonesResource.class);
    resources.add(AvailabilityZoneTasksResource.class);
    resources.add(ClusterResource.class);
    resources.add(ClusterResizeResource.class);
    resources.add(ClusterVmsResource.class);
    resources.add(DatastoreResource.class);
    resources.add(DatastoresResource.class);
    resources.add(DeploymentHostsResource.class);
    resources.add(DeploymentResource.class);
    resources.add(DeploymentsResource.class);
    resources.add(DeploymentVmsResource.class);
    resources.add(DeploymentAdminGroupsResource.class);
    resources.add(DiskResource.class);
    resources.add(DiskTasksResource.class);
    resources.add(FlavorResource.class);
    resources.add(FlavorsResource.class);
    resources.add(FlavorTasksResource.class);
    resources.add(HostResource.class);
    resources.add(HostTasksResource.class);
    resources.add(HostVmsResource.class);
    resources.add(NetworkPortGroupsSetResource.class);
    resources.add(NetworkResource.class);
    resources.add(NetworksResource.class);
    resources.add(ImageResource.class);
    resources.add(ImagesResource.class);
    resources.add(ImageTasksResource.class);
    resources.add(ProjectClustersResource.class);
    resources.add(ProjectDisksResource.class);
    resources.add(PortGroupResource.class);
    resources.add(PortGroupsResource.class);
    resources.add(ProjectResource.class);
    resources.add(ProjectTasksResource.class);
    resources.add(ProjectVmsResource.class);
    resources.add(ProjectSecurityGroupsResource.class);
    resources.add(ResourceTicketResource.class);
    resources.add(ResourceTicketTasksResource.class);
    resources.add(StatusResource.class);
    resources.add(TaskResource.class);
    resources.add(TasksResource.class);
    resources.add(TenantProjectsResource.class);
    resources.add(TenantResource.class);
    resources.add(TenantResourceTicketsResource.class);
    resources.add(TenantsResource.class);
    resources.add(TenantTasksResource.class);
    resources.add(TenantSecurityGroupsResource.class);
    resources.add(VmDiskAttachResource.class);
    resources.add(VmDiskDetachResource.class);
    resources.add(VmIsoAttachResource.class);
    resources.add(VmIsoDetachResource.class);
    resources.add(VmMetadataSetResource.class);
    resources.add(VmMksTicketResource.class);
    resources.add(VmNetworksResource.class);
    resources.add(VmResource.class);
    resources.add(VmTagsResource.class);
    resources.add(VmTasksResource.class);

    environment.jersey().register(new SwaggerJsonListing(resources, SWAGGER_VERSION, "v1"));
  }

  private GuiceBundle<ApiFeStaticConfiguration> getGuiceBundle() {
    return GuiceBundle.<ApiFeStaticConfiguration>newBuilder()
        .setConfigClass(ApiFeStaticConfiguration.class)
        .addModule(apiModule)
        .addModule(zookeeperModule)
        .enableAutoConfig(getClass().getPackage().getName())
        .build();
  }
}
