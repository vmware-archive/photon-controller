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
import com.vmware.photon.controller.apife.filter.NetworkToSubnetRedirectionFilter;
import com.vmware.photon.controller.apife.filter.PauseFilter;
import com.vmware.photon.controller.apife.resources.auth.AuthResource;
import com.vmware.photon.controller.apife.resources.availabilityzone.AvailabilityZoneResource;
import com.vmware.photon.controller.apife.resources.availabilityzone.AvailabilityZonesResource;
import com.vmware.photon.controller.apife.resources.cluster.ClusterResizeResource;
import com.vmware.photon.controller.apife.resources.cluster.ClusterResource;
import com.vmware.photon.controller.apife.resources.cluster.ProjectClustersResource;
import com.vmware.photon.controller.apife.resources.datastore.DatastoreResource;
import com.vmware.photon.controller.apife.resources.datastore.DatastoresResource;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentAdminGroupsResource;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentResource;
import com.vmware.photon.controller.apife.resources.deployment.DeploymentsResource;
import com.vmware.photon.controller.apife.resources.disk.DiskResource;
import com.vmware.photon.controller.apife.resources.disk.ProjectDisksResource;
import com.vmware.photon.controller.apife.resources.flavor.FlavorResource;
import com.vmware.photon.controller.apife.resources.flavor.FlavorsResource;
import com.vmware.photon.controller.apife.resources.host.DeploymentHostsResource;
import com.vmware.photon.controller.apife.resources.host.HostResource;
import com.vmware.photon.controller.apife.resources.image.ImageResource;
import com.vmware.photon.controller.apife.resources.image.ImagesResource;
import com.vmware.photon.controller.apife.resources.physicalnetwork.SubnetPortGroupsSetResource;
import com.vmware.photon.controller.apife.resources.physicalnetwork.SubnetResource;
import com.vmware.photon.controller.apife.resources.physicalnetwork.SubnetsResource;
import com.vmware.photon.controller.apife.resources.portgroup.PortGroupResource;
import com.vmware.photon.controller.apife.resources.portgroup.PortGroupsResource;
import com.vmware.photon.controller.apife.resources.project.ProjectResource;
import com.vmware.photon.controller.apife.resources.project.ProjectSecurityGroupsResource;
import com.vmware.photon.controller.apife.resources.project.TenantProjectsResource;
import com.vmware.photon.controller.apife.resources.resourceticket.ResourceTicketResource;
import com.vmware.photon.controller.apife.resources.resourceticket.TenantResourceTicketsResource;
import com.vmware.photon.controller.apife.resources.status.AvailableResource;
import com.vmware.photon.controller.apife.resources.status.StatusResource;
import com.vmware.photon.controller.apife.resources.tasks.AvailabilityZoneTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.DiskTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.FlavorTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.HostTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.ImageTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.ProjectTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.ResourceTicketTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.TaskResource;
import com.vmware.photon.controller.apife.resources.tasks.TasksResource;
import com.vmware.photon.controller.apife.resources.tasks.TenantTasksResource;
import com.vmware.photon.controller.apife.resources.tasks.VmTasksResource;
import com.vmware.photon.controller.apife.resources.tenant.TenantResource;
import com.vmware.photon.controller.apife.resources.tenant.TenantSecurityGroupsResource;
import com.vmware.photon.controller.apife.resources.tenant.TenantsResource;
import com.vmware.photon.controller.apife.resources.virtualnetwork.ProjectNetworksResource;
import com.vmware.photon.controller.apife.resources.vm.ClusterVmsResource;
import com.vmware.photon.controller.apife.resources.vm.DeploymentVmsResource;
import com.vmware.photon.controller.apife.resources.vm.HostVmsResource;
import com.vmware.photon.controller.apife.resources.vm.ProjectVmsResource;
import com.vmware.photon.controller.apife.resources.vm.VmDiskAttachResource;
import com.vmware.photon.controller.apife.resources.vm.VmDiskDetachResource;
import com.vmware.photon.controller.apife.resources.vm.VmImageCreateResource;
import com.vmware.photon.controller.apife.resources.vm.VmIsoAttachResource;
import com.vmware.photon.controller.apife.resources.vm.VmIsoDetachResource;
import com.vmware.photon.controller.apife.resources.vm.VmMetadataSetResource;
import com.vmware.photon.controller.apife.resources.vm.VmMksTicketResource;
import com.vmware.photon.controller.apife.resources.vm.VmNetworksResource;
import com.vmware.photon.controller.apife.resources.vm.VmResource;
import com.vmware.photon.controller.apife.resources.vm.VmTagsResource;
import com.vmware.photon.controller.common.metrics.GraphiteConfig;
import com.vmware.photon.controller.swagger.resources.SwaggerJsonListing;

import com.google.inject.Injector;
import com.hubspot.dropwizard.guice.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import javax.servlet.DispatcherType;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  private HibernateBundle<ApiFeStaticConfiguration> hibernateBundle;

  public static void main(String[] args) throws Exception {
    setupApiFeConfigurationForServerCommand(args);
    new ApiFeService().run(args);
  }

  /**
   * Best effort to parse configuration file as in `bin/management server /etc/esxcloud/photon-controller-core.yml`.
   * It does not work with commands such as
   * `management db migrate /etc/esxcloud/photon-controller-core.yml`
   * as in {@link io.dropwizard.migrations.DbCommand}
   */
  public static void setupApiFeConfigurationForServerCommand(String[] args)
      throws IOException, ConfigurationException {
    if (args.length == 2 && "server".equals(args[0])) {
      apiFeConfiguration = ConfigurationUtils.parseConfiguration(args[1]);
    }
  }

  @Override
  public void initialize(Bootstrap<ApiFeStaticConfiguration> bootstrap) {
    bootstrap.addBundle(new AssetsBundle("/assets", "/api/", "index.html"));

    apiModule = new ApiFeModule();
    apiModule.setConfiguration(apiFeConfiguration);

    GuiceBundle<ApiFeStaticConfiguration> guiceBundle = getGuiceBundle();
    bootstrap.addBundle(guiceBundle);

    injector = guiceBundle.getInjector();
  }

  @Override
  public void run(ApiFeStaticConfiguration configuration, Environment environment) throws Exception {
    environment.jersey().register(NetworkToSubnetRedirectionFilter.class);
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
    environment.jersey().register(new ExternalExceptionMapper());
    environment.jersey().register(new ConstraintViolationExceptionMapper());
    environment.jersey().register(new JsonProcessingExceptionMapper());
    environment.jersey().register(new LoggingExceptionMapper());
    environment.jersey().register(new WebApplicationExceptionMapper());

    environment.servlets().addFilter("LoggingFilter", injector.getInstance(LoggingFilter.class))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    environment.servlets().addFilter("UrlTrailingSlashFilter", injector.getInstance(UrlTrailingSlashFilter.class))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/api");

    GraphiteConfig graphite = configuration.getGraphite();
    if (graphite != null) {
      graphite.enable();
    }
  }

  private void registerResourcesWithSwagger(ApiFeConfiguration configuration, Environment environment) {
    List<Class<?>> resources = new ArrayList<>();
    resources.add(AuthResource.class);
    resources.add(AvailableResource.class);
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
    resources.add(VmImageCreateResource.class);
    resources.add(VmIsoAttachResource.class);
    resources.add(VmIsoDetachResource.class);
    resources.add(VmMetadataSetResource.class);
    resources.add(VmMksTicketResource.class);
    resources.add(VmNetworksResource.class);
    resources.add(VmResource.class);
    resources.add(VmTagsResource.class);
    resources.add(VmTasksResource.class);

    if (!apiFeConfiguration.useVirtualNetwork()) {
      resources.add(SubnetPortGroupsSetResource.class);
      resources.add(SubnetResource.class);
      resources.add(SubnetsResource.class);
    } else {
      resources.add(com.vmware.photon.controller.apife.resources.virtualnetwork.SubnetResource.class);
      resources.add(ProjectNetworksResource.class);
    }

    environment.jersey().register(new SwaggerJsonListing(resources, SWAGGER_VERSION, "v1"));
  }

  private GuiceBundle<ApiFeStaticConfiguration> getGuiceBundle() {
    String[] packages = getResourcePackagesToStart();

    return GuiceBundle.<ApiFeStaticConfiguration>newBuilder()
        .setConfigClass(ApiFeStaticConfiguration.class)
        .addModule(apiModule)
        .enableAutoConfig(packages)
        .build();
  }

  /**
   * Based on api-fe configuration, the list of resources is to be returned.
   *
   * @return
   */
  private String[] getResourcePackagesToStart() {
    String currPackageName = getClass().getPackage().getName();
    String resourcesPrefix = currPackageName + ".resources";

    Set<String> packagesToExclude = new HashSet<>();
    if (apiFeConfiguration.useVirtualNetwork()) {
      packagesToExclude.add(resourcesPrefix + ".physicalnetwork");
    } else {
      packagesToExclude.add(resourcesPrefix + ".virtualnetwork");
    }

    Reflections reflections = new Reflections(
        new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage(currPackageName))
            .setScanners(new SubTypesScanner(false))
            .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(resourcesPrefix)))
    );

    Set<Class<? extends Object>> classes = reflections.getSubTypesOf(Object.class);

    Set<String> packages = new HashSet<>();
    for (Class classInstance : classes) {
      String packageName = classInstance.getPackage().getName();
      if (!packagesToExclude.contains(packageName)) {
        packages.add(classInstance.getPackage().getName());
      }
    }

    return packages.toArray(new String[0]);
  }
}
