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
package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The HostCache is used by the scheduler to track information on what hosts
 * are available to be used for scheduling decisions.
 *
 * It can answer a query for "give me N random hosts that match a set of constraints".
 *
 * It has methods used for introspection (mostly for testing) to tell us about the
 * state of the cache.
 */
public class HostCache {

  /**
   * The ConcurrentMultiMap lets us store a map from a key to a set of values. It's a ConcurrentHashMap, where each
   * value is a ConcurrentHashMap. It's similar to Guava's Multimap, but thread-safe. It provides a limited
   * set of operations to support just the host cache.
   *
   * We use this, for example, to keep track of all the hosts that have a given datastore.
   *
   */
  private static class ConcurrentMultiMap<K, V> {
    private ConcurrentHashMap<K, Set<V>> map;

    public ConcurrentMultiMap() {
      map = new ConcurrentHashMap<>();
    }

    /**
     * Add a value to the set of values associated with a key.
     */
    private void addOrUpdate(K key, V value) {
      if (!this.map.contains(key)) {
        Set<V> set = Collections.newSetFromMap(new ConcurrentHashMap<V, Boolean>());
        map.putIfAbsent(key,  set);
      }
      Set<V> set = map.get(key);
      set.add(value);
    }

    /**
     * Replace the entire set of values associated with a key.
     */
    private void setAll(K key, Set<V> values) {
      if (values == null) {
        map.remove(key);
        return;
      }
      map.put(key, values);
    }

    /**
     * Remove a key and all of it's associated values.
     */
    private void remove(K key) {
      map.remove(key);
    }

    /**
     * For each key, remove the given value from its set of values.
     * Obviously, this may be an expensive operation: use it judiciously.
     */
    private void removeFromAll(V value) {
      for (Set<V> values : map.values()) {
        values.remove(value);
      }
    }

    /**
     * Get the set of values associated with a key.
     *
     * This should be used for introspection only: to add or delete values, user other methods.
     */
    private Set<V> getValues(K key) {
      return map.get(key);
    }

    /**
     * The number of keys in this ConcurrentMultiMap.
     */
    private int size() {
      return map.size();
    }

  }

  private static final Logger logger = LoggerFactory.getLogger(HostCache.class);

  ServiceHost schedulerHost;

  // Map from host ID to host address
  protected ConcurrentHashMap<String, ServerAddress> hostAddresses;

  // Set of management host IDs
  protected Set<String> managementHostIds;

  // Map from network ID to set of host IDs
  protected ConcurrentMultiMap<String, String> networksToHosts;

  // Map from datastore ID to set of host IDs
  protected ConcurrentMultiMap<String, String> datastoresToHosts;

  // Map from datastore ID to datastore tags
  protected ConcurrentMultiMap<String, String> datastoresToTags;

  // Set of management host IDs; based on ConcurrentHashMap for thread-safety
  // Map from datastore tag to set of host IDs
  protected ConcurrentMultiMap<String, String> datastoreTagsToHosts;

  // Map from availability zone to set of host IDs
  protected ConcurrentMultiMap<String, String> availabilityZonesToHosts;


  public HostCache(ServiceHost schedulerHost, DcpRestClient cloudstoreClient) {
    this.schedulerHost = schedulerHost;
    initializeCache();
    subscribeToHosts(cloudstoreClient, createHostNotificationTarget());
    subscribeToDatastores(cloudstoreClient, createDatastoreNotificationTarget());
  }

  /**
   * The HostCache is still in progress. This will return a set of hosts from the cache that match
   * the given constraints.
   */
  public Map<String, ServerAddress> getHostsMatchingContraints(List<ResourceConstraint> constraints, int numHosts) {
    throw new IllegalStateException("Not implemented yet");
  }

  /**
   * Returns the number of hosts in the host cache.
   * Intended for testing
   */
  public int getNumberHosts() {
    return this.hostAddresses.size();
  }

  /**
   * Returns the number of management hosts in the host cache.
   * Intended for testing
   */
  public int getNumberManagementHosts() {
    return this.managementHostIds.size();
  }

  /**
   * Returns the number of datastores in the host cache.
   * Intended for testing
   */
  public int getNumberDatastores() {
    return this.datastoresToTags.size();
  }

  /**
   * Returns the set of host IDs associated with a datastore.
   * Intended for testing
   */
  public Set<String> getHostsWithDatastore(String datastoreId) {
    return this.datastoresToHosts.getValues(datastoreId);
  }

  /**
   * Returns the set of host IDs associated with a datastore tag.
   * Intended for testing.
   */
  public Set<String> getHostsWithDatastoreTag(String datastoreTag) {
    return this.datastoreTagsToHosts.getValues(datastoreTag);
  }

  /**
   * Returns the set of host IDs associated with a network.
   * Intended for testing.
   */
  public Set<String> getHostsOnNetwork(String networkId) {
    return this.networksToHosts.getValues(networkId);
  }

  /**
   * Returns the set of host IDs associated with an availability zone.
   * Intended for testing.
   */
  public Set<String> getHostsInZone(String availabilityZone) {
    return this.availabilityZonesToHosts.getValues(availabilityZone);
  }

  /**
   * Initialize all of our maps in the cache.
   */
  private void initializeCache() {
    hostAddresses = new ConcurrentHashMap<>();
    managementHostIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    networksToHosts = new ConcurrentMultiMap<>();
    datastoresToHosts = new ConcurrentMultiMap<>();
    datastoreTagsToHosts = new ConcurrentMultiMap<>();
    datastoresToTags = new ConcurrentMultiMap<>();
    availabilityZonesToHosts = new ConcurrentMultiMap<>();
  }

  /**
   * Create a lambda that can be used to respond to changes in the set of hosts. When it is
   * notified that a host has been added, modified or deleted, the cache will be updated
   * appropriately.
   */
  private Consumer<Operation> createHostNotificationTarget() {
    Consumer<Operation> notificationTarget = (queryUpdate) -> {
        queryUpdate.complete();

        if (!queryUpdate.hasBody() && queryUpdate.getAction() == Service.Action.DELETE) {
            // TODO(alainr): recreate query when someone deletes it
            logger.info("Continuous query was deleted, no further updates to host cache will happen");
            return;
        }

        Map<String, Object> documents = extractDocumentsFromQueryUpdate(queryUpdate, "host");
        if (documents == null) {
          // Log message happened in extractDocumentsFromQueryUpdate
          return;
        }
        for (Object document : documents.values()) {
          HostService.State host = Utils.fromJson(document, HostService.State.class);
          if (host == null) {
            logger.warn("Host query had invalid host, ignoring");
            continue;
          }
          if (host.documentUpdateAction.equals(Service.Action.POST.toString())
              || host.documentUpdateAction.equals(Service.Action.PUT.toString())
              || host.documentUpdateAction.equals(Service.Action.PATCH.toString())) {
            addOrUpdateHost(host);
          } else if (host.documentUpdateAction.equals(Service.Action.DELETE.toString())) {
            deleteHost(host);
          }
        }
    };
    return notificationTarget;
  }

  /**
   * Create a lambda that can be used to respond to changes in the set of hosts. When it is
   * notified that a host has been added, modified or deleted, the cache will be updated
   * appropriately.
   */
  private Consumer<Operation> createDatastoreNotificationTarget() {
    Consumer<Operation> notificationTarget = (queryUpdate) -> {
      queryUpdate.complete();

      if (!queryUpdate.hasBody() && queryUpdate.getAction() == Service.Action.DELETE) {
          // TODO(alainr): recreate query when someone deletes it
          logger.info("Continuous query was deleted, no further updates to datastore cache will happen");
          return;
      }

      Map<String, Object> documents = extractDocumentsFromQueryUpdate(queryUpdate, "datastore");
      if (documents == null) {
        // Log message happened in extractDocumentsFromQueryUpdate
        return;
      }
      for (Object document : documents.values()) {
        DatastoreService.State host = Utils.fromJson(document, DatastoreService.State.class);
        if (host == null) {
          logger.warn("Host query had invalid host, ignoring");
          continue;
        }
        if (host.documentUpdateAction.equals(Service.Action.POST.toString())
            || host.documentUpdateAction.equals(Service.Action.PUT.toString())
            || host.documentUpdateAction.equals(Service.Action.PATCH.toString())) {
          addOrUpdateDatastore(host);
        } else if (host.documentUpdateAction.equals(Service.Action.DELETE.toString())) {
          deleteDatastore(host);
        }
      }
    };
    return notificationTarget;
  }

  /**
   * Used by the notification targets to extract the "documents" from the QueryTask.
   *
   * Returns null if the documents can't be extracted.
   */
  private Map<String, Object> extractDocumentsFromQueryUpdate(Operation queryUpdate, String entityType) {
    if (!queryUpdate.hasBody()) {
      logger.info("Got {} query update with action = {} with no body", entityType, queryUpdate.getAction());
      return null;
    }

    QueryTask taskState = queryUpdate.getBody(QueryTask.class);
    if (taskState == null) {
      logger.warn("Got empty {} query notification", entityType);
      return null;
    }
    ServiceDocumentQueryResult queryResult = taskState.results;
    if (queryResult == null) {
      logger.warn("Got {} query notification with empty result", entityType);
      return null;
    }
    Map<String, Object> documents = queryResult.documents;
    if (documents == null) {
      logger.warn("Got {} query notification with empty documents", entityType);
      return null;
    }
    return documents;
  }

  /**
   * Subscribe to the HostService, to receive notifications of changes to the hosts.
   */
  private void subscribeToHosts(DcpRestClient cloudstoreClient, Consumer<Operation> notificationTarget) {
    subscribeToCloudstoreEntities(
        cloudstoreClient,
        "host",
        Utils.buildKind(HostService.State.class),
        notificationTarget);
  }

  /**
   * Subscribe to the DatastoreService, to receive notifications of changes to the datastore.
   */
  private void subscribeToDatastores(DcpRestClient cloudstoreClient, Consumer<Operation> notificationTarget) {
    subscribeToCloudstoreEntities(
        cloudstoreClient,
        "datastore",
        Utils.buildKind(DatastoreService.State.class),
        notificationTarget);
  }

  /**
   * Helper used by subscribeToHosts and subscribeToDatastores to subscribe.
   */
  private void subscribeToCloudstoreEntities(
      DcpRestClient cloudstoreClient,
      String entityName,
      String entityType,
      Consumer<Operation> notificationTarget) {

    // Create a continuous query task for the hosts
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(entityType);
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    querySpecification.options = EnumSet.of(
        QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT,
        QueryTask.QuerySpecification.QueryOption.CONTINUOUS);
    Operation completedOp;
    try {
      completedOp = cloudstoreClient.query(querySpecification, false);
    } catch (Throwable ex) {
      logger.warn("Could not create continuous {} query task, all scheduler requests will fail. Exception: {}",
          entityName, Utils.toString(ex));
      return;
    }

    // Verify that it worked
    if (completedOp.getStatusCode() >= Operation.STATUS_CODE_FAILURE_THRESHOLD) {
      logger.warn("Could not create continuous {} query task, all scheduler requests will fail. Status: {}",
          entityName, completedOp.getStatusCode());
      return;
    }
    QueryTask queryResult = completedOp.getBody(QueryTask.class);
    if (queryResult == null) {
      logger.warn("Could not create continuous {} query task: Empty response. All scheduler requests will fail.",
          entityName);
      return;
    }
    String taskPath = queryResult.documentSelfLink;
    if (taskPath == null) {
      logger.warn("Continuous {} query task has no self link. All scheduler requests will fail.", entityName);
      return;
    }

    URI taskUri = cloudstoreClient.getServiceUri(taskPath);

    // Now subscribe to the continuous query so we learn about all changes
    // Note that we request that the subscription replays the state, so our first
    // notification will include all hosts that currently exist
    Operation subscribe = Operation.createPost(taskUri).setReferer(this.schedulerHost.getPublicUri());
    URI subscriptionUri = this.schedulerHost.startReliableSubscriptionService(subscribe, notificationTarget);
    logger.info("Subscribed to {} query task at {}", entityName, taskUri);
    logger.info("SubscriptionUri: {}", subscriptionUri);
  }

  /**
   * This method is called by the host notification target when a host is added or modified.
   * It updates the cache appropriately.
   */
  private void addOrUpdateHost(HostService.State host) {
    String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);
    ServerAddress hostAddress = new ServerAddress(host.hostAddress, host.agentPort);

    this.hostAddresses.put(hostId, hostAddress);

    if (host.usageTags != null && host.usageTags.contains(UsageTag.MGMT.name())) {
      this.managementHostIds.add(hostId);
    }

    if (host.reportedNetworks != null) {
      for (String networkId: host.reportedNetworks) {
        if (networkId != null) {
          this.networksToHosts.addOrUpdate(networkId, hostId);
        }
      }
    }

    if (host.reportedDatastores != null) {
      for (String datastoreId : host.reportedDatastores) {
        datastoresToHosts.addOrUpdate(datastoreId, hostId);
        Set<String> datastoreTags = this.datastoresToTags.getValues(datastoreId);
        if (datastoreTags != null) {
          for (String datastoreTag : datastoreTags) {
            this.datastoreTagsToHosts.addOrUpdate(datastoreTag, hostId);
          }
        }
      }
    }

    if (host.availabilityZoneId != null) {
      this.availabilityZonesToHosts.addOrUpdate(host.availabilityZoneId, hostId);
    }
  }

  /**
   * This method is called by the datastore notification target when a datastore is added or modified.
   * It updates the cache appropriately.
   */
  private void addOrUpdateDatastore(DatastoreService.State datastore) {
    String datastoreId = ServiceUtils.getIDFromDocumentSelfLink(datastore.documentSelfLink);

    this.datastoresToTags.setAll(datastoreId, datastore.tags);

    // TODO(alainr): Update datastoreTagsToHosts
  }

  /**
   * This method is called by the host notification target when a host is deleted
   * It updates the cache appropriately.
   */
  private void deleteHost(HostService.State host) {
    String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);

    this.hostAddresses.remove(hostId);
    this.managementHostIds.remove(hostId);

    // We aggressively remove the host from all the maps it might belong to.
    // In theory, we shouldn't have to because we know the list of things to
    // remove it from. However, if anything is inconsistent, we could leave
    // behind old host information, so we clean more aggressively. Host removal
    // is a rare operation, so we're willing to have some extra expense in order
    // to do it correctly.
    this.networksToHosts.removeFromAll(hostId);
    this.datastoresToHosts.removeFromAll(hostId);
    this.datastoreTagsToHosts.removeFromAll(hostId);
    this.availabilityZonesToHosts.removeFromAll(hostId);
  }

  /**
   * This method is called by the datastore notification target when a datastore is deleted
   * It updates the cache appropriately.
   */
  private void deleteDatastore(DatastoreService.State datastore) {
    String datastoreId = ServiceUtils.getIDFromDocumentSelfLink(datastore.documentSelfLink);

    this.datastoresToTags.remove(datastoreId);
    // TODO(alainr): Update datastoreTagsToHosts
  }

}
