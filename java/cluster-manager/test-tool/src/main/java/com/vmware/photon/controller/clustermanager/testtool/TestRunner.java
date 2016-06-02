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

package com.vmware.photon.controller.clustermanager.testtool;

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.RestClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
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
 * Runs the cluster manager tests.
 */
public class TestRunner {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private static final String API_FE_PORT = "9000";
  private static final String DEPLOYER_PORT = "18000";

  private static final long CLUSTER_POLL_INTERVAL_SECONDS = 15;
  private static final long CLUSTER_POLL_RETRIES = 240;

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
    this.apiClient = new ApiClient(apiAddress, httpClient, null);

    String deployerAddress = args.getManagementVmAddress() + ":" + DEPLOYER_PORT;
    this.deployerClient = new RestClient(deployerAddress, httpClient);
  }

  public List<TestStats> run() throws Exception {
    List<TestStats> statsList = new ArrayList<>();
    for (int i = 0; i < arguments.getClusterCount(); i++) {
      TestStats stats = new TestStats();

      try {
        // Create the Cluster
        String clusterId = createCluster(stats);

        // Wait for the Cluster to be Ready
        waitForReady(clusterId, stats);
      } catch (Throwable ex) {
        logger.error(ex.toString());
      } finally {

        // Delete the cluster.
        cleanupClusters();
      }

      statsList.add(stats);
    }

    return statsList;
  }

  private String createCluster(TestStats stats) throws Exception {
    logger.info("Started to create Cluster");
    ClusterCreateSpec spec = createClusterCreateSpec(arguments.getClusterType());

    StopWatch sw = new StopWatch();
    sw.start();

    Task task = apiClient.getProjectApi().createCluster(arguments.getProjectId(), spec);
    task = pollTask(task.getId());

    sw.stop();

    String clusterId;
    TestStats.TestStep step = new TestStats.TestStep();

    step.name = "CREATE_CLUSTER";
    step.timeTakenMilliSeconds = sw.getTime();
    step.passed = false;

    if (task != null) {
      if (task.getState().equalsIgnoreCase(TaskService.State.TaskState.COMPLETED.toString())) {
        step.passed = true;
        clusterId = task.getEntity().getId();
        logger.info("Cluster Created. ClusterId: " + clusterId);
      } else {
        throw new RuntimeException("Failed to create Cluster. Failure:" +
            task.getResourceProperties().toString());
      }
    } else {
      throw new RuntimeException("Timed-out to create Cluster.");
    }

    stats.testStepList.add(step);
    return clusterId;
  }

  private ClusterCreateSpec createClusterCreateSpec(ClusterType clusterType) {

    ClusterCreateSpec spec = new ClusterCreateSpec();
    if (clusterType == ClusterType.KUBERNETES) {
      spec.setName("KubernetesCluster");
      spec.setType(clusterType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP, arguments.getStaticIps()[0]);
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      spec.setExtendedProperties(extendedProperties);
    } else if (clusterType == ClusterType.MESOS) {
      spec.setName("MesosCluster");
      spec.setType(clusterType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put("zookeeper_ip1", arguments.getStaticIps()[0]);
      extendedProperties.put("zookeeper_ip2", arguments.getStaticIps()[1]);
      extendedProperties.put("zookeeper_ip3", arguments.getStaticIps()[2]);
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      spec.setExtendedProperties(extendedProperties);

    } else if (clusterType == ClusterType.SWARM) {
      spec.setName("SwarmCluster");
      spec.setType(clusterType);
      spec.setSlaveCount(arguments.getSlaveCount());

      Map<String, String> extendedProperties = new HashMap<>();
      extendedProperties.put("etcd_ip1", arguments.getStaticIps()[0]);
      extendedProperties.put("etcd_ip2", arguments.getStaticIps()[1]);
      extendedProperties.put("etcd_ip3", arguments.getStaticIps()[2]);
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, arguments.getDns());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, arguments.getGateway());
      extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, arguments.getNetmask());
      spec.setExtendedProperties(extendedProperties);
    }

    return spec;
  }

  private void waitForReady(String clusterId, TestStats stats) throws Exception {
    TestStats.TestStep step = new TestStats.TestStep();
    step.name = "CLUSTER_EXPANSION";
    step.passed = false;

    StopWatch sw = new StopWatch();
    sw.start();

    Cluster cluster = null;
    int retryCount = 0;

    while (retryCount++ < CLUSTER_POLL_RETRIES) {
      cluster = apiClient.getClusterApi().getCluster(clusterId);
      if (cluster.getState() == ClusterState.READY) {
        logger.info("Cluster reached the READY state successfully.");
        sw.stop();

        step.timeTakenMilliSeconds = sw.getTime();
        step.passed = true;
        break;
      }

      // Wait and Retry
      Thread.sleep(TimeUnit.SECONDS.toMillis(CLUSTER_POLL_INTERVAL_SECONDS));
    }

    if (!step.passed) {
      throw new RuntimeException("Cluster failed to reach the READY state. Current clusterState: " +
          cluster.getState());
    }

    stats.testStepList.add(step);
  }

  private void cleanupClusters() throws Exception {
    // Delete the clusters
    ResourceList<Cluster> clusters = apiClient.getProjectApi().getClustersInProject(arguments.getProjectId());
    for (Cluster cluster : clusters.getItems()) {
      String clusterId = cluster.getId();

      boolean deleted = false;
      while (!deleted) {
        try {
          deleteCluster(clusterId);
          deleted = true;
        } catch (Throwable ex) {
          // Catch all errors and keep retrying until the cluster is deleted successfully.
        }
      }
    }

    //deleteServices(ServiceUriPaths.CLUSTERMANAGER_ROOT + "/cluster-maintenance-tasks");
  }

  private void deleteCluster(String clusterId) throws Exception {
    Task task = apiClient.getClusterApi().delete(clusterId);
    task = pollTask(task.getId());
    if (task != null) {
      if (task.getState().equalsIgnoreCase(TaskService.State.TaskState.COMPLETED.toString())) {
        logger.info("Cluster deleted. ClusterId: " + clusterId);
      } else {
        throw new RuntimeException("Failed to delete Cluster. Failure:" +
            task.getResourceProperties().toString());
      }
    } else {
      throw new RuntimeException("Timed-out to delete Cluster.");
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
