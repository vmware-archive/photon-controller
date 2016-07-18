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
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class represents a simple Rest Client to call into Kubernetes Rest APIs to query the status of the cluster.
 */
public class KubernetesClient {
  private static final String GET_NODES_PATH = "/api/v1/nodes";

  private CloseableHttpAsyncClient httpClient;
  private ObjectMapper objectMapper;

  public KubernetesClient(CloseableHttpAsyncClient httpClient) {
    Preconditions.checkNotNull(httpClient);

    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();

    // Ignore unknown properties
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * This method calls into the Kubernetes API endpoint to retrieve the node ip addresses.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getNodeAddressesAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {

            Nodes response = null;
            try {
              restClient.checkResponse(result, HttpStatus.SC_OK);
              response = objectMapper.readValue(result.getEntity().getContent(), new TypeReference<Nodes>() {
              });
            } catch (Throwable e) {
              callback.onFailure(e);
              return;
            }

            Set<String> nodes = new HashSet();
            if (response != null && response.items != null) {
              for (Node n : response.items) {
                if (n.status != null && n.status.addresses != null) {
                  for (NodeAddress a : n.status.addresses) {
                    nodes.add(a.address);
                  }
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
                new RuntimeException("getNodeAddressesAsync was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, GET_NODES_PATH, null, futureCallback);
  }

  /**
   * This method calls into the Kubernetes API endpoint to retrieve the hostnames of slave nodes.
   *
   * @param connectionString          connectionString of the master Node in the Cluster
   * @param callback                  callback that is invoked on completion of the operation.
   * @throws IOException
   */
  public void getNodeNamesAsync(
      final String connectionString,
      final FutureCallback<Set<String>> callback) throws IOException {

    final RestClient restClient = new RestClient(connectionString, this.httpClient);

    org.apache.http.concurrent.FutureCallback futureCallback =
        new org.apache.http.concurrent.FutureCallback<HttpResponse>() {
          @Override
          public void completed(HttpResponse result) {

            Nodes response = null;
            try {
              restClient.checkResponse(result, HttpStatus.SC_OK);
              response = objectMapper.readValue(result.getEntity().getContent(), new TypeReference<Nodes>() {
              });
            } catch (Throwable e) {
              callback.onFailure(e);
              return;
            }

            Set<String> nodes = new HashSet();
            if (response != null && response.items != null) {
              for (Node n : response.items) {
                if (n.getMetadata() != null && n.getMetadata().getName() != null) {
                  nodes.add(n.getMetadata().getName());
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
                new RuntimeException("getNodeNamesAsync was cancelled"));
          }
        };

    restClient.performAsync(RestClient.Method.GET, GET_NODES_PATH, null, futureCallback);
  }

  /**
   * Represents the contract object used to represent Kubernetes Cluster Nodes,
   * as returned by the GET /nodes API.
   */
  public static class Nodes {
    private List<Node> items;

    public List<Node> getItems() {
      return this.items;
    }
    public void setItems(List<Node> items) {
      this.items = items;
    }
  }

  /**
   * Represents the contract object used to represent a specific Kubernetes Cluster Node,
   * as returned by the GET /nodes API.
   */
  public static class Node {
    private NodeMetadata metadata;
    private NodeStatus status;

    public NodeMetadata getMetadata() {
      return metadata;
    }

    public void setMetadata(NodeMetadata metadata) {
      this.metadata = metadata;
    }

    public NodeStatus getStatus() {
      return this.status;
    }
    public void setStatus(NodeStatus status) {
      this.status = status;
    }
  }

  /**
   * Represents the contract object used to represent the metadata of a node.
   */
  public static class NodeMetadata {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  /**
   * Represents the contract object used to represent the status of a node.
   */
  public static class NodeStatus {
    private List<NodeAddress> addresses;

    public List<NodeAddress> getAddresses() {
      return this.addresses;
    }
    public void setAddresses(List<NodeAddress> addresses) {
      this.addresses = addresses;
    }
  }

  /**
   * Represents the contract object used to represent the address of a node.
   */
  public static class NodeAddress {
    private String address;
    private String type;

    public String getAddress() {
      return this.address;
    }
    public String getType() {
      return this.type;
    }
    public void setAddress(String address) {
      this.address = address;
    }
    public void setType(String type) {
      this.type = type;
    }
  }
}
