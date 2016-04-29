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
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker.GetCandidatesCompletion;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An immutable, in-memory implementation of {@link ConstraintChecker}.
 *
 * This will be removed after the conversion to the new flat scheduler is complete
 * The equivalent in the new flat scheduler is the HostCache
 */
public class InMemoryConstraintChecker implements ConstraintChecker {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryConstraintChecker.class);

  // Map from host ID to host:port
  private final ImmutableMap<String, ServerAddress> hosts;

  // Set of management hosts
  private final ImmutableSet<String> managementHosts;

  // Map from network ID to host IDs
  private final ImmutableSetMultimap<String, String> networks;

  // Map from datastore ID to host IDs
  private final ImmutableSetMultimap<String, String> datastores;

  // Map from datastore tag to host IDs
  private final ImmutableSetMultimap<String, String> datastoreTags;

  // Map from availability zone to host IDs
  private final ImmutableSetMultimap<String, String> availabilityZones;

  @Inject
  public InMemoryConstraintChecker(XenonRestClient client) {
    Map<String, HostService.State> hosts = new HashMap<>();
    Map<String, DatastoreService.State> datastores = new HashMap<>();

    try {
      // Fetch all the hosts from cloudstore
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(HostService.State.class));
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = kindClause;
      querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
      Operation completedOp = client.query(querySpecification, true);
      ServiceDocumentQueryResult queryResult = completedOp.getBody(QueryTask.class).results;
      for (Map.Entry<String, Object> result : queryResult.documents.entrySet()) {
        HostService.State hostState = Utils.fromJson(result.getValue(), HostService.State.class);
        final int trimLength = (HostServiceFactory.SELF_LINK + "/").length();
        String hostId = hostState.documentSelfLink.substring(trimLength);
        hosts.put(hostId, hostState);
      }

      // Fetch all the datastores from cloudstore
      kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));
      querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = kindClause;
      querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
      completedOp = client.query(querySpecification, true);
      queryResult = completedOp.getBody(QueryTask.class).results;
      for (Map.Entry<String, Object> result : queryResult.documents.entrySet()) {
        DatastoreService.State datastoreState = Utils.fromJson(result.getValue(), DatastoreService.State.class);
        final int trimLength = (DatastoreServiceFactory.SELF_LINK + "/").length();
        String datastoreId = datastoreState.documentSelfLink.substring(trimLength);
        datastores.put(datastoreId, datastoreState);
      }
    } catch (Throwable ex) {
      logger.warn("Failed to fetch host/datastore documents from cloudstore", ex);
    }

    ImmutableMap.Builder<String, ServerAddress> hostBuilder = new ImmutableMap.Builder<>();
    ImmutableSet.Builder<String> managementHostBuilder = new ImmutableSet.Builder<>();
    ImmutableSetMultimap.Builder<String, String> datastoreBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> datastoreTagBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> networkBuilder = new ImmutableSetMultimap.Builder<>();
    ImmutableSetMultimap.Builder<String, String> availabilityZoneBuilder = new ImmutableSetMultimap.Builder<>();

    for (Map.Entry<String, HostService.State> host: hosts.entrySet()) {
      if (host.getValue().reportedDatastores == null) {
        logger.warn("Ignoring {}. The reportedDatastores field is null.", host);
        continue;
      }
      if (host.getValue().reportedNetworks == null) {
        logger.warn("Ignoring {}. The reportedNetworks field is null.", host);
        continue;
      }
      if (host.getValue().usageTags == null) {
        logger.warn("Ignoring {}. The usageTags field is null.", host);
        continue;
      }

      hostBuilder.put(host.getKey(), new ServerAddress(host.getValue().hostAddress, host.getValue().agentPort));
      if (host.getValue().usageTags.contains(UsageTag.MGMT.name())) {
        managementHostBuilder.add(host.getKey());
      }
      for (String datastoreId: host.getValue().reportedDatastores) {
        datastoreBuilder.put(datastoreId, host.getKey());
        DatastoreService.State datastore = datastores.get(datastoreId);
        if (datastore != null && datastore.tags != null) {
          for (String datastoreTag : datastore.tags) {
            datastoreTagBuilder.put(datastoreTag, host.getKey());
          }
        }
      }
      for (String networkId: host.getValue().reportedNetworks) {
        networkBuilder.put(networkId, host.getKey());
      }

      if (host.getValue().availabilityZoneId == null) {
        logger.info("{} doesn't have the availabilityZone field set.", host);
      } else {
        availabilityZoneBuilder.put(host.getValue().availabilityZoneId, host.getKey());
      }
    }
    this.hosts = hostBuilder.build();
    this.managementHosts = managementHostBuilder.build();
    this.datastores = datastoreBuilder.build();
    this.datastoreTags = datastoreTagBuilder.build();
    this.networks = networkBuilder.build();
    this.availabilityZones = availabilityZoneBuilder.build();
  }

  /**
   * Returns a set of host IDs that satisfy a given constraint.
   *
   * @param constraint
   * @return a set of hostIDs that satisfy a given constraint.
   */
  private ImmutableSet<String> checkConstraint(ResourceConstraint constraint) {
    ImmutableSet<String> matches;
    if (!constraint.isSetValues() || constraint.getValues().size() != 1) {
      throw new IllegalArgumentException("Invalid constraint with multiple values: " + constraint);
    }
    String value = constraint.getValues().get(0);
    if (constraint.getType() == ResourceConstraintType.AVAILABILITY_ZONE) {
      if (constraint.isNegative()) {
        matches = getHostsNotInAvailabilityZone(value);
      } else {
        matches = getHostsInAvailabilityZone(value);
      }
    } else if (constraint.getType() == ResourceConstraintType.DATASTORE) {
      matches = getHostsWithDatastore(value);
    } else if (constraint.getType() == ResourceConstraintType.DATASTORE_TAG) {
      matches = getHostsWithDatastoreTag(value);
    } else if (constraint.getType() == ResourceConstraintType.HOST) {
      if (constraint.isNegative()) {
        matches = getHostsExceptFor(value);
      } else {
        if (getHosts().contains(value)) {
          matches = ImmutableSet.of(value);
        } else {
          matches = ImmutableSet.of();
        }
      }
    } else if (constraint.getType() == ResourceConstraintType.MANAGEMENT_ONLY) {
      matches = getManagementHosts();
    } else if (constraint.getType() == ResourceConstraintType.NETWORK) {
      // TODO(mmutsuzaki) support multiple networks?
      matches = getHostsWithNetwork(value);
    } else {
      throw new IllegalArgumentException("Unsupported constraint type: " + constraint);
    }
    return matches;
  }

  @Override
  public Map<String, ServerAddress> getCandidatesSync(List<ResourceConstraint> constraints, int numCandidates) {
    // The day we make this code truly async, we'll need to do something real here, like have a completion and
    // wait for it.
    return getCandidatesHelper(constraints, numCandidates);
  }

  @Override
  public void getCandidates(
      List<ResourceConstraint> constraints,
      int numCandidates,
      String requestId,
      GetCandidatesCompletion completion) {

    // Note that we are not yet meaningfully asynchronous. We are slowly converting to an asynchronous pattern,
    // and this is a step in that direction.
    // The InMemoryConstraintChecker is not used in production, so it's possible we won't convert it soon.
    // The CloudStoreConstraintChecker is used in production, and it has the same interface, so we need
    // this interface, even if it's not fully asynchronous
    try {
      Map<String, ServerAddress> candidates = getCandidatesHelper(constraints, numCandidates);
      completion.handle(candidates, null);
    } catch (Exception ex) {
      completion.handle(null, ex);
    }
  }

  private Map<String, ServerAddress> getCandidatesHelper(List<ResourceConstraint> constraints, int numCandidates) {
    // Find all the hosts that satisfy the resource constraints.
    ImmutableSet<String> matches;
    if (constraints.isEmpty()) {
      matches = getHosts();
    } else {
      Iterator<ResourceConstraint> iterator = constraints.iterator();
      matches = checkConstraint(iterator.next());
      while (iterator.hasNext()) {
        matches = Sets.intersection(matches, checkConstraint(iterator.next())).immutableCopy();
      }
    }

    // Randomly pick candidates. Pretty sure there is a better way to do this...
    Map<String, ServerAddress> result = new HashMap<>();
    Map<String, ServerAddress> hostMap = getHostMap();
    while (result.size() < numCandidates && result.size() < matches.size()) {
      String pick = matches.asList().get(RANDOM.nextInt(matches.size()));
      result.put(pick, hostMap.get(pick));
    }
    return ImmutableMap.copyOf(result);
  }

  public ImmutableSet<String> getHostsInAvailabilityZone(String availabilityZone) {
    return availabilityZones.get(availabilityZone);
  }

  public ImmutableSet<String> getHostsNotInAvailabilityZone(String availabilityZone) {
    return Sets.difference(hosts.keySet(), availabilityZones.get(availabilityZone)).immutableCopy();
  }

  public ImmutableSet<String> getHostsWithDatastore(String datastoreId) {
    return datastores.get(datastoreId);
  }

  public ImmutableSet<String> getHostsWithDatastoreTag(String datastoreTag) {
    return datastoreTags.get(datastoreTag);
  }

  public ImmutableSet<String> getHostsWithNetwork(String networkId) {
    return networks.get(networkId);
  }

  public ImmutableSet<String> getHosts() {
    return hosts.keySet();
  }

  public ImmutableSet<String> getHostsExceptFor(String host) {
    return Sets.difference(hosts.keySet(), ImmutableSet.of(host)).immutableCopy();
  }

  public ImmutableSet<String> getManagementHosts() {
    return managementHosts;
  }

  public ImmutableMap<String, ServerAddress> getHostMap() {
    return hosts;
  }
}
