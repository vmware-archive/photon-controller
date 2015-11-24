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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceDocumentQueryResult;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class implements a constraint checker using the Lucene-based indexing and query
 * mechanism in DCP.
 */
public class CloudStoreConstraintChecker implements ConstraintChecker {

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintChecker.class);

  private DcpRestClient dcpRestClient;

  public CloudStoreConstraintChecker(DcpRestClient dcpRestClient) {
    this.dcpRestClient = dcpRestClient;
  }

  public ImmutableSet<String> getHostsWithDatastore(String datastoreId) {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getHostsWithNetwork(String networkId) {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getHostsInAvailabilityZone(String availabilityZone) {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getHostsNotInAvailabilityZone(String availabilityZone) {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getHostsWithDatastoreTag(String datastoreTag) {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getManagementHosts() {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableSet<String> getHosts() {
    throw new IllegalStateException("Not implemented");
  }

  public ImmutableMap<String, ServerAddress> getHostMap() {
    throw new IllegalStateException("Not implemented");
  }

  public Map<String, ServerAddress> getCandidates(List<ResourceConstraint> constraints, int numCandidates) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    querySpecification.resultLimit = numCandidates > 10 ? 10 : numCandidates;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    for (ResourceConstraint constraint : constraints) {

      if (!constraint.isSetValues() || constraint.getValues().size() != 1) {
        throw new IllegalArgumentException("Invalid constraint with multiple values: " + constraint);
      }

      switch (constraint.getType()) {
        case AVAILABILITY_ZONE:
          QueryTask.Query availabilityZoneClause = new QueryTask.Query()
              .setTermPropertyName(HostService.State.FIELD_NAME_AVAILABILITY_ZONE)
              .setTermMatchValue(constraint.getValues().get(0));
          querySpecification.query.addBooleanClause(availabilityZoneClause);
          break;
        case DATASTORE:
          QueryTask.Query datastoreClause = new QueryTask.Query()
              .setTermPropertyName(HostService.State.FIELD_NAME_REPORTED_DATASTORES)
              .setTermMatchValue(constraint.getValues().get(0));
          querySpecification.query.addBooleanClause(datastoreClause);
          break;
        case DATASTORE_TAG:
          throw new IllegalArgumentException("Datastore tags are not supported currently");
        case MANAGEMENT_ONLY:
          QueryTask.Query usageTagClause = new QueryTask.Query()
              .setTermPropertyName(HostService.State.FIELD_NAME_USAGE_TAGS)
              .setTermMatchValue(UsageTag.MGMT.name());
          querySpecification.query.addBooleanClause(usageTagClause);
          break;
        case NETWORK:
          QueryTask.Query networkClause = new QueryTask.Query()
              .setTermPropertyName(HostService.State.FIELD_NAME_REPORTED_NETWORKS)
              .setTermMatchValue(constraint.getValues().get(0));
          querySpecification.query.addBooleanClause(networkClause);
          break;
        default:
          throw new IllegalStateException("Unrecognized constraint type: " + constraint);
      }
    }

    try {
      Operation completedOp = dcpRestClient.query(querySpecification, true);
      ServiceDocumentQueryResult queryResult = completedOp.getBody(QueryTask.class).results;

      Map<String, ServerAddress> results = new HashMap<>(numCandidates);

      while (queryResult.nextPageLink != null && results.size() < numCandidates) {

        queryResult = dcpRestClient.queryDocumentPage(queryResult.nextPageLink);

        for (Map.Entry<String, Object> result : queryResult.documents.entrySet()) {
          HostService.State hostState = Utils.fromJson(result.getValue(), HostService.State.class);
          final int trimLength = (HostServiceFactory.SELF_LINK + "/").length();
          String documentSelfLink = hostState.documentSelfLink.substring(trimLength);
          ServerAddress serverAddress = new ServerAddress(hostState.hostAddress, DEFAULT_AGENT_PORT);
          results.put(documentSelfLink, serverAddress);
          if (results.size() >= numCandidates) {
            break;
          }
        }
      }

      return results;

    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}