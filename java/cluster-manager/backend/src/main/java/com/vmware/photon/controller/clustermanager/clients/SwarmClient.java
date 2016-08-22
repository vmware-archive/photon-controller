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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class represents a simple Rest Client to call into Swarm Rest APIs to query the status of the cluster.
 */
public class SwarmClient {
  private static final Logger logger = LoggerFactory.getLogger(SwarmClient.class);
  private static final String SWARM_STATUS_PATH = "/info";
  private static final String MASTER_VM_NAME_PREFIX = "master";
  private static final String WORKER_VM_NAME_PREFIX = "worker";

  private enum NodeProperty {
    IP_ADDRESS,
    HOSTNAME
  };

  private CloseableHttpAsyncClient httpClient;
  private ObjectMapper objectMapper;

  public SwarmClient(CloseableHttpAsyncClient httpClient) {
    Preconditions.checkNotNull(httpClient);

    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();

    // Ignore unknown properties
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * This method calls into the Swarm API endpoint to retrieve the node ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @param nodeProperty              specify the property of the node to return.
   * @throws IOException
   */
  private void getNodeStatusAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback,
      final NodeProperty nodeProperty) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {

            ArrayNode driverStatus;
            try {
              restClient.checkResponse(result, HttpStatus.SC_OK);
              JsonNode response = objectMapper.readTree(result.getEntity().getContent());
              driverStatus = (ArrayNode) response.get("DriverStatus");
            } catch (Throwable e) {
              callback.onFailure(e);
              return;
            }

            Set<String> nodes = new HashSet();

            for (JsonNode entry : driverStatus) {
              ArrayNode kv = (ArrayNode) entry;
              if (kv.size() == 2) {
                try {
                  String key = kv.get(0).asText();
                  if (key.startsWith(MASTER_VM_NAME_PREFIX) || key.startsWith(WORKER_VM_NAME_PREFIX)) {
                    switch (nodeProperty) {
                      case IP_ADDRESS:
                        String nodeAddress = kv.get(1).asText().split(":")[0];
                        nodes.add(nodeAddress);
                        break;
                      case HOSTNAME:
                        nodes.add(key);
                        break;
                      default:
                        new UnsupportedOperationException(
                            "NodeProperty is not supported. NodeProperty: " + nodeProperty);
                    }
                  }
                } catch (Exception ex) {
                  logger.error("malformed node value", ex);
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

    restClient.performAsync(RestClient.Method.GET, SWARM_STATUS_PATH, null, futureCallback);
  }

  /**
   * This method calls into the Swarm API endpoint to retrieve the node ip addresses.
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
   * This method calls into the Swarm API endpoint to retrieve the node names.
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
}
