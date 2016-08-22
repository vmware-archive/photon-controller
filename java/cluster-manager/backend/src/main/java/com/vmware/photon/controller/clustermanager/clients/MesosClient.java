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

package com.vmware.photon.controller.clustermanager.clients;

import com.vmware.photon.controller.api.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class represents a simple Rest Client to call into Mesos Rest APIs to query the status of the cluster.
 */
public class MesosClient {
  private static final Logger logger = LoggerFactory.getLogger(MesosClient.class);
  private static final String MASTER_STATE_PATH = "/master/state.json";
  private static final String MARATHON_PATH = "/v2/info";

  private enum NodeProperty {
    IP_ADDRESS,
    HOSTNAME
  };

  private CloseableHttpAsyncClient httpClient;
  private ObjectMapper objectMapper;

  public MesosClient(CloseableHttpAsyncClient httpClient) {
    Preconditions.checkNotNull(httpClient);

    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();

    // Ignore unknown properties
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * This method calls into the Mesos API endpoint to retrieve the master leader ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getMasterLeader(
      final String connectionString,
      final FutureCallback<String> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {

            final String leaderStringPrefix = "master@";
            MasterState response;
            try {
              restClient.checkResponse(result, HttpStatus.SC_OK);
              response = objectMapper.readValue(result.getEntity().getContent(), new TypeReference<MasterState>() {
              });
            } catch (Throwable e) {
              callback.onFailure(e);
              return;
            }

            if (StringUtils.isNotBlank(response.getLeader()) &&
                response.getLeader().startsWith(leaderStringPrefix)) {
              callback.onSuccess(response.getLeader().replace(leaderStringPrefix, "http://"));
            } else {
              callback.onFailure(new RuntimeException("failed to get leader address."));
            }
          }

          @Override
          public void failed(Exception ex) {
            callback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            callback.onFailure(
                new RuntimeException("getMasterLeader was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, MASTER_STATE_PATH, null, futureCallback);
  }

  /**
   * This method calls into the Mesos API endpoint to retrieve the node ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @param nodeProperty              specify the property of the node to return.
   * @throws IOException
   */
  public void getNodeStatusAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback,
      final NodeProperty nodeProperty) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {

            MasterState response;
            try {
              restClient.checkResponse(result, HttpStatus.SC_OK);
              response = objectMapper.readValue(result.getEntity().getContent(), new TypeReference<MasterState>() {
              });
            } catch (Throwable e) {
              callback.onFailure(e);
              return;
            }

            Set<String> nodes = new HashSet();
            if (response != null && response.workers != null) {
              for (Worker s : response.workers) {
                switch (nodeProperty) {
                  case IP_ADDRESS:
                    try {
                      String nodeAddress = s.getPid().split("@|:")[1];
                      nodes.add(nodeAddress);
                    } catch (Exception ex) {
                      logger.error("malformed worker pid: " + s.getPid());
                    }
                    break;
                  case HOSTNAME:
                    if (s.getHostname() != null) {
                      nodes.add(s.getHostname());
                    }
                    break;
                  default:
                    new UnsupportedOperationException(
                        "NodeProperty is not supported. NodeProperty: " + nodeProperty);
                }
              }
            }

            callback.onSuccess(nodes);
          }

          @Override
          public void failed(Exception ex) {
            callback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            callback.onFailure(
                new RuntimeException("getNodeStatusAsync was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, MASTER_STATE_PATH, null, futureCallback);
  }

  /**
   * This method calls into the Mesos API endpoint to retrieve the node ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getNodeAddressesAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback) throws IOException {

    getNodeStatusAsync(connectionString, callback, NodeProperty.IP_ADDRESS);
  }

  /**
   * This method calls into the Mesos API endpoint to retrieve the node ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getNodeNamesAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback) throws IOException {

    getNodeStatusAsync(connectionString, callback, NodeProperty.HOSTNAME);
  }

  /**
   * This method calls into the Marathon API endpoint and determine its status
   * based on http status.
   *
   * @param connectionString          connectionString of the marathon Node
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void checkMarathon(
      final String connectionString,
      final FutureCallback<Boolean> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {
            callback.onSuccess(result.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
          }

          @Override
          public void failed(Exception ex) {
            callback.onFailure(ex);
          }

          @Override
          public void cancelled() {
            callback.onFailure(
                new RuntimeException("checkMarathon was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, MARATHON_PATH, null, futureCallback);
  }

  /**
   * Represents the contract object used to represent Mesos Cluster State,
   * as returned by the GET /nodes API.
   */
  public static class MasterState {
    private String leader;
    private List<Worker> workers;

    public String getLeader() {
      return leader;
    }

    public void setLeader(String leader) {
      this.leader = leader;
    }

    public List<Worker> getWorkers() {
      return workers;
    }

    public void setWorkers(List<Worker> workers) {
      this.workers = workers;
    }
  }

  /**
   * Represents the contract object used to represent a specific Mesos Cluster Worker,
   * as returned by the GET /nodes API.
   */
  public static class Worker {
    private String hostname;
    private String pid;

    public String getHostname() {
      return hostname;
    }

    public void setHostname(String hostname) {
      this.hostname = hostname;
    }

    public String getPid() {
      return pid;
    }

    public void setPid(String pid) {
      this.pid = pid;
    }
  }
}
