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
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * This class implements a {@link ConstraintChecker} using DCP queries against cloud store nodes.
 */
public class CloudStoreConstraintChecker implements ConstraintChecker {

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintChecker.class);

  private final Random random = new Random();

  private final DcpRestClient dcpRestClient;

  @Inject
  public CloudStoreConstraintChecker(DcpRestClient dcpRestClient) {
    this.dcpRestClient = dcpRestClient;
  }

  public Map<String, ServerAddress> getCandidates(List<ResourceConstraint> resourceConstraints, int numCandidates) {

    Map<String, ServerAddress> result = new HashMap<>(numCandidates);
    long lowerBound;
    long upperBound = 0;

    while (result.size() < numCandidates) {

      //
      // If the entire scheduling constant space has been searched, then return the
      // partial result set. Otherwise, pick a new portion of the space to search.
      //

      if (upperBound == 10 * 1000) {
        break;
      }

      lowerBound = upperBound;
      upperBound = lowerBound + 1 + random.nextInt(10 * 1000);
      if (upperBound > 10 * 1000) {
        upperBound = 10 * 1000;
      }

      //
      // Query the host documents in the scheduling constant space which match the
      // given criteria.
      //

      QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
          .addKindFieldClause(HostService.State.class)
          .addRangeClause(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
              QueryTask.NumericRange.createLongRange(lowerBound, upperBound, true, false));

      for (ResourceConstraint constraint : resourceConstraints) {

        if (!constraint.isSetValues() || constraint.getValues().size() > 1) {
          throw new IllegalArgumentException("Invalid resource constraint: " + constraint);
        }

        switch (constraint.getType()) {
          case AVAILABILITY_ZONE:
            queryBuilder.addFieldClause(HostService.State.FIELD_NAME_AVAILABILITY_ZONE,
                constraint.getValues().get(0),
                constraint.isSetNegative() && constraint.isNegative() ?
                    QueryTask.Query.Occurance.MUST_NOT_OCCUR :
                    QueryTask.Query.Occurance.MUST_OCCUR);
            break;
          case DATASTORE:
            queryBuilder.addCollectionItemClause(HostService.State.FIELD_NAME_REPORTED_DATASTORES,
                constraint.getValues().get(0));
            break;
          case DATASTORE_TAG:
            queryBuilder.addClause(getDatastoreTagClause(constraint.getValues().get(0)));
            break;
          case HOST:
            queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                HostServiceFactory.SELF_LINK + "/" + constraint.getValues().get(0),
                constraint.isSetNegative() && constraint.isNegative() ?
                    QueryTask.Query.Occurance.MUST_NOT_OCCUR :
                    QueryTask.Query.Occurance.MUST_OCCUR);
            break;
          case MANAGEMENT_ONLY:
            queryBuilder.addCollectionItemClause(HostService.State.FIELD_NAME_USAGE_TAGS,
                UsageTag.MGMT.name());
            break;
          case NETWORK:
            queryBuilder.addCollectionItemClause(HostService.State.FIELD_NAME_REPORTED_NETWORKS,
                constraint.getValues().get(0));
            break;
          default:
            throw new IllegalStateException("Invalid resource constraint: " + constraint);
        }
      }

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(queryBuilder.build())
          .orderDescending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
              ServiceDocumentDescription.TypeName.LONG)
          .setResultLimit(numCandidates)
          .build();

      try {
        Operation completedOp = dcpRestClient.query(queryTask);
        ServiceDocumentQueryResult queryResult = completedOp.getBody(QueryTask.class).results;
        Set<String> documentLinks = new HashSet<>(numCandidates);
        while (queryResult.nextPageLink != null && documentLinks.size() < numCandidates) {
          queryResult = dcpRestClient.queryDocumentPage(queryResult.nextPageLink);
          documentLinks.addAll(queryResult.documentLinks);
        }

        if (documentLinks.size() > 0) {
          Map<String, Operation> operations = dcpRestClient.get(documentLinks, documentLinks.size());
          for (Map.Entry<String, Operation> entry : operations.entrySet()) {
            result.put(ServiceUtils.getIDFromDocumentSelfLink(entry.getKey()), new ServerAddress(
                entry.getValue().getBody(HostService.State.class).hostAddress, DEFAULT_AGENT_PORT));
          }
        }
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }

    return result;
  }

  private QueryTask.Query getDatastoreTagClause(String tag) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DatastoreService.State.class)
            .addCollectionItemClause(DatastoreService.State.FIELD_NAME_TAGS, tag)
            .build())
        .setResultLimit(1000)
        .build();

    try {
      Operation completedOp = dcpRestClient.query(queryTask);
      ServiceDocumentQueryResult queryResult = completedOp.getBody(QueryTask.class).results;
      Set<String> documentLinks = new HashSet<>();
      while (queryResult.nextPageLink != null) {
        queryResult = dcpRestClient.queryDocumentPage(queryResult.nextPageLink);
        documentLinks.addAll(queryResult.documentLinks);
      }

      QueryTask.Query.Builder builder = QueryTask.Query.Builder.create();
      for (String documentLink : documentLinks) {
        builder.addCollectionItemClause(HostService.State.FIELD_NAME_REPORTED_DATASTORES,
            ServiceUtils.getIDFromDocumentSelfLink(documentLink),
            QueryTask.Query.Occurance.SHOULD_OCCUR);
      }

      return builder.build();

    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
