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

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * This class implements a {@link ConstraintChecker} using Xenon queries against cloud store nodes.
 */
public class CloudStoreConstraintChecker implements ConstraintChecker {

  private static final String HOST_SELF_LINK_PREFIX = HostServiceFactory.SELF_LINK + "/";
  private static final List<String> managementTagValues = Arrays.asList(UsageTag.MGMT.name());

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintChecker.class);

  private final Random random = new Random();

  private final DcpRestClient xenonRestClient;

  @Inject
  public CloudStoreConstraintChecker(DcpRestClient xenonRestClient) {
    this.xenonRestClient = xenonRestClient;
  }

  /**
   * Select a set of up to numCandidates random hosts that match the by given constraints querying Cloudstore.
   *
   * This constructs a query to CloudStore. It's interesting, because we want random hosts, but Xenon's
   * LuceneDocumentQueryService cannot select randomly. To get randomness, each HostService document is assigned a
   * random number between 0 and 10,000 when it's created. We pick a random number and try to find hosts close to that
   * random number.
   *
   * More precisely, we divide the hosts into two sets based on this number:
   *
   * 0.....random midpoint.....10,000
   *
   * We first look for results in the second half (random to 10,000), sorting them in ascending order. If we don't find
   * enough hosts, we query from the first half (0 to random), and we sort them in descending order. (50% of the time,
   * we do it in the reverse order, to reduce bias against hosts with a low scheduling constant.) Note that empirical
   * results shows we still have some bias because we're not making a truly random selection.
   *
   * We're searching querying approximately 50% of the hosts at a time, which means that when we have a lot of hosts,
   * Lucene has to do a big sort. If we find that this is a performance hit, we can search smaller intervals, but that
   * also may mean more queries to Lucene when we don't have a lot of hosts.
   */
  @Override
  public Map<String, ServerAddress> getCandidates(List<ResourceConstraint> resourceConstraints, int numCandidates) {

    if (numCandidates <= 0) {
      throw new IllegalArgumentException("getCandidates called with invalid numCandidates: " + numCandidates);
    }

    Map<String, ServerAddress> result = new HashMap<>(numCandidates);

    try {
      // Divide the hosts into two groups, based on a random midpoint
      int randomMidpoint = 1 + random.nextInt(HostService.MAX_SCHEDULING_CONSTANT - 1);

      if (random.nextBoolean()) {
        // Case 1: first try [randomMidpoint, 10000], then [0, randomMidpoint]
        QueryTask.Query query =
            buildQuery(resourceConstraints, randomMidpoint, HostService.MAX_SCHEDULING_CONSTANT);
        getCandidates(query, numCandidates, SortOrder.ASC, result);

        if (result.size() < numCandidates) {
          updateQueryRange(query, 0, randomMidpoint);
          getCandidates(query, numCandidates, SortOrder.DESC, result);
        }
      } else {
        // Case 2: first try [0, randomMidpoint] then [randomMidpoint, 10000]
        QueryTask.Query query =
            buildQuery(resourceConstraints, 0, randomMidpoint);
        getCandidates(query, numCandidates, SortOrder.DESC, result);

        if (result.size() < numCandidates) {
          updateQueryRange(query, randomMidpoint, HostService.MAX_SCHEDULING_CONSTANT);
          getCandidates(query, numCandidates, SortOrder.ASC, result);
        }
      }
    } catch (NoSuchResourceException e) {
      logger.warn(e.getMessage());
    }

    return result;
  }

  /**
   * Build the query that we'll use to query Cloudstore. We translate each constraint to a clause in a Xenon query.
   */
  private QueryTask.Query buildQuery(
      List<ResourceConstraint> resourceConstraints,
      long lowerBound,
      long upperBound) throws NoSuchResourceException {
    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(HostService.State.class)
        .addRangeClause(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
            QueryTask.NumericRange.createLongRange(lowerBound, upperBound, true, false));

    // Ensure that we only look for hosts that are ready (not, for example, suspended)
    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_STATE, HostState.READY);

    // Ensure that we only look for hosts that are responsive. Those are hosts with agents that respond
    // to pings and are marked as active.
    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_AGENT_STATE, AgentState.ACTIVE);

    if (resourceConstraints != null) {
      for (ResourceConstraint constraint : resourceConstraints) {

        if (constraint == null) {
          continue;
        }

        switch (constraint.getType()) {
          case AVAILABILITY_ZONE:
            addFieldClause(queryBuilder, HostService.State.FIELD_NAME_AVAILABILITY_ZONE_ID, null, constraint);
            break;
          case DATASTORE:
            addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_REPORTED_DATASTORES, constraint);
            break;
          case DATASTORE_TAG:
            addDatastoreTagClause(queryBuilder, constraint);
            break;
          case HOST:
            addFieldClause(queryBuilder, HostService.State.FIELD_NAME_SELF_LINK, HOST_SELF_LINK_PREFIX, constraint);
            break;
          case MANAGEMENT_ONLY:
            // This constraint doesn't come in with values, but we want to reuse our code, so we set them
            constraint.setValues(managementTagValues);
            addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_USAGE_TAGS, constraint);
            break;
          case NETWORK:
            addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_REPORTED_NETWORKS, constraint);
            break;
          default:
            throw new IllegalStateException("Invalid resource constraint: " + constraint);
        }
      }
    }

    return queryBuilder.build();
  }

  /**
   * A helper method for creating a clause for a single constraint.
   */
  private void addFieldClause(
      QueryTask.Query.Builder builder,
      String fieldName,
      String valuePrefix,
      ResourceConstraint constraint) {
    List<String> values = constraint.getValues();
    boolean isNegative = false;
    if (constraint.isSetNegative() && constraint.isNegative()) {
      isNegative = true;
    }
    QueryTask.Query.Occurance occurance =
        isNegative ? QueryTask.Query.Occurance.MUST_NOT_OCCUR : QueryTask.Query.Occurance.MUST_OCCUR;

    if (values == null || values.size() == 0) {
      return;
    }

    // If there is just one value, we add a simple clause
    if (values.size() == 1) {
      String value;
      if (valuePrefix != null) {
        value = valuePrefix + values.get(0);
      } else {
        value = values.get(0);
      }
      builder.addFieldClause(fieldName, value, occurance);
      return;
    }

    // If there are multiple values, we make a new OR clause
    // We could use Builder.addInClause() here, but we need to prefix each value
    QueryTask.Query.Builder innerBuilder = QueryTask.Query.Builder.create(occurance);
    for (String value : values) {
      if (valuePrefix != null) {
        value = valuePrefix + value;
      }
      innerBuilder.addFieldClause(fieldName, value, Occurance.SHOULD_OCCUR);
    }
    builder.addClause(innerBuilder.build());
  }

  /**
   * A helper method for creating a clause for a single collection constraint.
   */
  private void addCollectionItemClause(
      QueryTask.Query.Builder builder,
      String fieldName,
      ResourceConstraint constraint) {
    List<String> values = constraint.getValues();
    boolean isNegative = false;
    if (constraint.isSetNegative() && constraint.isNegative()) {
      isNegative = true;
    }
    QueryTask.Query.Occurance occurance =
        isNegative ? QueryTask.Query.Occurance.MUST_NOT_OCCUR : QueryTask.Query.Occurance.MUST_OCCUR;

    if (values == null || values.size() == 0) {
      return;
    }

    // The simple case: there is only one value in the constraint
    if (values.size() == 1) {
      builder.addCollectionItemClause(fieldName, values.get(0), occurance);
      return;
    }

    // If there are multiple values, we make a new OR clause
    builder.addInCollectionItemClause(fieldName, values, occurance);
  }

  /**
   * Update the range in the query, so we find a different set of hosts.
   */
  private void updateQueryRange(QueryTask.Query query, long lowerBound, long upperBound) {
    for (QueryTask.Query queryTerm : query.booleanClauses) {
      if (queryTerm != null && queryTerm.term != null && queryTerm.term.propertyName != null) {
        if (queryTerm.term.propertyName.equals(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT)) {
          queryTerm.term.range = QueryTask.NumericRange.createLongRange(lowerBound, upperBound, true, false);
          break;
        }
      }
    }
  }

  /**
   * Submit the query to CloudStore.
   */
  private void getCandidates(
      QueryTask.Query query,
      int numCandidates,
      SortOrder sortOrder,
      Map<String, ServerAddress> result) {

    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(query)
        .setResultLimit(numCandidates)
        .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    if (sortOrder == SortOrder.ASC) {
      queryTaskBuilder.orderAscending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
          ServiceDocumentDescription.TypeName.LONG);
    } else {
      queryTaskBuilder.orderDescending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
          ServiceDocumentDescription.TypeName.LONG);
    }

    QueryTask queryTask = queryTaskBuilder.build();

    try {
      Operation completedOp = xenonRestClient.query(queryTask);
      Map<String, Object> documents = extractDocumentsFromQuery(completedOp);

      if (documents == null) {
        // Logging happened in extractDocumentsFromQuery
        return;
      }

      for (Object document : documents.values()) {
        HostService.State host = Utils.fromJson(document, HostService.State.class);
        if (host == null) {
          logger.warn("Host query had invalid host, ignoring");
          continue;
        }
        result.put(ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink),
            new ServerAddress(host.hostAddress, host.agentPort));

        if (result.size() >= numCandidates) {
          // If we're searching the second half of the search space, we need to make sure not to add too many
          // candidates.
          break;
        }
      }
    } catch (Throwable t) {
      logger.warn("Failed to query Cloudstore for hosts matching {}, exception: {}",
          Utils.toJsonHtml(query), Utils.toString(t));
    }
  }

  /**
   * Helper for getCandidates() to extract the set of documents (full host records) from the query.
   *
   * @param queryResponse
   * @return
   */
  private Map<String, Object> extractDocumentsFromQuery(Operation queryResponse) {
    if (!queryResponse.hasBody()) {
      logger.info("Got host query response without a body");
      return null;
    }

    QueryTask taskState = queryResponse.getBody(QueryTask.class);
    if (taskState == null) {
      logger.warn("Got empty host query response");
      return null;
    }
    ServiceDocumentQueryResult queryResult = taskState.results;
    if (queryResult == null) {
      logger.warn("Got host query response with empty result");
      return null;
    }
    Map<String, Object> documents = queryResult.documents;
    if (documents == null) {
      logger.warn("Got host query response with empty documents");
      return null;
    }
    return documents;
  }


  private void addDatastoreTagClause(QueryTask.Query.Builder builder, ResourceConstraint constraint) throws
      NoSuchResourceException {

    List<String> tags = constraint.getValues();
    if (tags == null || tags.size() == 0) {
      return;
    }

    // Build a query task for the datastores that have the tags we want
    QueryTask.Query.Builder tagBuilder = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.MUST_OCCUR);
    for (String tag : tags) {
      tagBuilder.addCollectionItemClause(
          DatastoreService.State.FIELD_NAME_TAGS, tag, QueryTask.Query.Occurance.SHOULD_OCCUR);
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DatastoreService.State.class)
            .addClause(tagBuilder.build())
            .build())
        .setResultLimit(1000)
        .build();

    try {
      // Query for the datastores that have the tags we want
      Operation completedOp = xenonRestClient.query(queryTask);
      ServiceDocumentQueryResult queryResult = completedOp.getBody(QueryTask.class).results;
      List<String> documentLinks = new ArrayList<>();

      if (queryResult.nextPageLink != null) {
        queryResult.nextPageLink = Base64.getEncoder().encodeToString(queryResult.nextPageLink.getBytes());
      }

      while (queryResult.nextPageLink != null) {
        queryResult = xenonRestClient.queryDocumentPage(queryResult.nextPageLink);
        for (String documentLink : queryResult.documentLinks) {
          documentLinks.add(ServiceUtils.getIDFromDocumentSelfLink(documentLink));
        }
      }

      // No datastore which match the specified constraint is available
      if (documentLinks.size() == 0) {
        throw new NoSuchResourceException("Cannot satisfy datastore tag constraint no datastores with tag(s) " +
            constraint.getValues().toString() + " found");
      }

      // Based on the datastores we got, build a new clause to select the datastores.
      ResourceConstraint datastoreConstraint = new ResourceConstraint(
          ResourceConstraintType.DATASTORE,
          documentLinks);
      if (constraint.isSetNegative() && constraint.isNegative()) {
        datastoreConstraint.setNegative(true);
      }
      addCollectionItemClause(builder, HostService.State.FIELD_NAME_REPORTED_DATASTORES, datastoreConstraint);

    } catch (NoSuchResourceException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
