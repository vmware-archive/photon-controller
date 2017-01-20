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

package com.vmware.photon.controller.servicesmanager.testtool;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.RestApiClient;
import com.vmware.photon.controller.api.client.RestClient;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceState;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the services manager tests.
 */
public class TestRunner {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private static final String API_FE_PORT = "9000";
  private static final String DEPLOYER_PORT = "18000";

  private static final long SERVICE_POLL_INTERVAL_SECONDS = 15;
  private static final long SERVICE_POLL_RETRIES = 240;

  private static final long TASK_POLL_INTERVAL_SECONDS = 5;
  private static final long TASK_RETRIES = 60;

  private Arguments arguments;
  private ApiClient apiClient;
  private RestClient deployerClient;

  public TestRunner(Arguments args) {

    this.arguments = args;

    CloseableHttpAsyncClient httpClient = HttpAsyncClientBuilder.create().build();
    httpClient.start();

    String apiAddress = args.getManagementVmAddress() + ":" + API_FE_PORT;
    this.apiClient = new RestApiClient(apiAddress, httpClient, null, "http");

    String deployerAddress = args.getManagementVmAddress() + ":" + DEPLOYER_PORT;
    this.deployerClient = new RestClient(deployerAddress, httpClient);
  }

  public List<TestStats> run() throws Exception {
    List<TestStats> statsList = new ArrayList<>();
    for (int i = 0; i < arguments.getServiceCount(); i++) {
      TestStats stats = new TestStats();

      try {
        // Create the Service
        String serviceId = createService(stats);

        // Wait for the Service to be Ready
        waitForReady(serviceId, stats);
      } catch (Throwable ex) {
        logger.error(ex.toString());
      } finally {

        // Delete the service.
        cleanupServices();
      }

      statsList.add(stats);
    }

    return statsList;
  }

  private String createService(TestStats stats) throws Exception {
    logger.info("Started to create Service");
    ServiceCreateSpec spec = createServiceCreateSpec(arguments.getServiceType());

    StopWatch sw = new StopWatch();
    sw.start();

    Task task = apiClient.getProjectApi().createService(arguments.getProjectId(), spec);
    task = pollTask(task.getId());

    sw.stop();

    String serviceId;
    TestStats.TestStep step = new TestStats.TestStep();

    step.name = "CREATE_SERVICE";
    step.timeTakenMilliSeconds = sw.getTime();
    step.passed = false;

    if (task != null) {
      if (task.getState().equalsIgnoreCase(TaskService.State.TaskState.COMPLETED.toString())) {
        step.passed = true;
        serviceId = task.getEntity().getId();
        logger.info("Service Created. ServiceId: " + serviceId);
      } else {
        throw new RuntimeException("Failed to create Service. Failure:" +
            task.getResourceProperties().toString());
      }
    } else {
      throw new RuntimeException("Timed-out to create Service.");
    }

    stats.testStepList.add(step);
    return serviceId;
  }

  private ServiceCreateSpec createServiceCreateSpec(ServiceType serviceType) {

    ServiceCreateSpec spec = new ServiceCreateSpec();
    if (serviceType == ServiceType.KUBERNETES) {
      spec.setName("KubernetesService");
      spec.setType(serviceType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, arguments.getStaticIps()[0]);
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      spec.setExtendedProperties(extendedProperties);
    } else if (serviceType == ServiceType.MESOS) {
      spec.setName("MesosService");
      spec.setType(serviceType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put("zookeeper_ip1", arguments.getStaticIps()[0]);
      extendedProperties.put("zookeeper_ip2", arguments.getStaticIps()[1]);
      extendedProperties.put("zookeeper_ip3", arguments.getStaticIps()[2]);
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      spec.setExtendedProperties(extendedProperties);

    } else if (serviceType == ServiceType.SWARM) {
      spec.setName("SwarmService");
      spec.setType(serviceType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put("etcd_ip1", arguments.getStaticIps()[0]);
      extendedProperties.put("etcd_ip2", arguments.getStaticIps()[1]);
      extendedProperties.put("etcd_ip3", arguments.getStaticIps()[2]);
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      spec.setExtendedProperties(extendedProperties);
    } else if (serviceType == ServiceType.HARBOR) {
      spec.setName("HarborService");
      spec.setType(serviceType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, arguments.getStaticIps()[0]);
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      spec.setExtendedProperties(extendedProperties);
    }

    return spec;
  }

  private void waitForReady(String serviceId, TestStats stats) throws Exception {
    TestStats.TestStep step = new TestStats.TestStep();
    step.name = "SERVICE_EXPANSION";
    step.passed = false;

    StopWatch sw = new StopWatch();
    sw.start();

    Service service = null;
    int retryCount = 0;

    while (retryCount++ < SERVICE_POLL_RETRIES) {
      service = apiClient.getServiceApi().getService(serviceId);
      if (service.getState() == ServiceState.READY) {
        logger.info("Service reached the READY state successfully.");
        sw.stop();

        step.timeTakenMilliSeconds = sw.getTime();
        step.passed = true;
        break;
      }

      // Wait and Retry
      Thread.sleep(TimeUnit.SECONDS.toMillis(SERVICE_POLL_INTERVAL_SECONDS));
    }

    if (!step.passed) {
      if (service != null) {
        throw new RuntimeException("Service failed to reach the READY state. Current serviceState: " +
            service.getState());
      } else {
        throw new RuntimeException("Service failed to reach the READY state. Current Service is null");
      }

    }

    stats.testStepList.add(step);
  }

  private void cleanupServices() throws Exception {
    // Delete the services
    ResourceList<Service> services = apiClient.getProjectApi().getServicesInProject(arguments.getProjectId());
    for (Service service : services.getItems()) {
      String serviceId = service.getId();

      boolean deleted = false;
      while (!deleted) {
        try {
          deleteService(serviceId);
          deleted = true;
        } catch (Throwable ex) {
          // Catch all errors and keep retrying until the service is deleted successfully.
        }
      }
    }

    //deleteServices(ServiceUriPaths.SERVICESMANAGER_ROOT + "/service-maintenance-tasks");
  }

  private void deleteService(String serviceId) throws Exception {
    Task task = apiClient.getServiceApi().delete(serviceId);
    task = pollTask(task.getId());
    if (task != null) {
      if (task.getState().equalsIgnoreCase(TaskService.State.TaskState.COMPLETED.toString())) {
        logger.info("Service deleted. ServiceId: " + serviceId);
      } else {
        throw new RuntimeException("Failed to delete Service. Failure:" +
            task.getResourceProperties().toString());
      }
    } else {
      throw new RuntimeException("Timed-out to delete Service.");
    }
  }

  private void deleteServices(String factoryServiceUrl) throws Exception {
    HttpResponse httpResponse = deployerClient.perform(RestClient.Method.GET, factoryServiceUrl, null);
    deployerClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    ServiceDocumentQueryResult result = deployerClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ServiceDocumentQueryResult>() {
        }
    );
    for (String documentLink : result.documentLinks) {
      deployerClient.perform(
          RestClient.Method.DELETE,
          documentLink,
          new StringEntity("{}", ContentType.APPLICATION_JSON));
    }
  }

  private Task pollTask(String taskId) throws Exception {
    int retryCount = 0;
    while (retryCount++ < TASK_RETRIES) {
      Task task = apiClient.getTasksApi().getTask(taskId);
      if (task.getState().equalsIgnoreCase(TaskService.State.TaskState.COMPLETED.toString()) ||
          task.getState().equalsIgnoreCase(TaskService.State.TaskState.ERROR.toString())) {
        return task;
      }
      // Wait and Retry
      Thread.sleep(TimeUnit.SECONDS.toMillis(TASK_POLL_INTERVAL_SECONDS));
    }
    return null;
  }
}
