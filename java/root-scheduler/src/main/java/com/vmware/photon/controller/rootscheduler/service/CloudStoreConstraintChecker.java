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
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * This class implements a {@link ConstraintChecker} using Xenon queries against cloud store nodes.
 */
public class CloudStoreConstraintChecker implements ConstraintChecker {

  private static final String HOST_SELF_LINK_PREFIX = HostServiceFactory.SELF_LINK + "/";
  private static final List<String> managementTagValues = Arrays.asList(UsageTag.MGMT.name());

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintChecker.class);

  private final Random random = new Random();

  private final XenonRestClient xenonRestClient;
  private final CloudStoreHelper cloudStoreHelper;
  private NettyHttpServiceClient xenonClient;

  private class Range {
    int lowerBound;
    int upperBound;
    SortOrder sortOrder;
  }

  private enum Step {
    VALIDATE_INPUT,
    CALCULATE_RANGES,
    BUILD_QUERY,
    GET_CANDIDATES,
    ANALYZE_CANDIDATES,
    SUCCESS,
    FAIL
  }

  private class State {

    // Input parameters
    List<ResourceConstraint> resourceConstraints;
    int numCandidates;
    String requestId;
    GetCandidatesCompletion completion;

    // Internal state
    Step currentStep;
    Range ranges[];
    int currentRange;
    QueryTask.Query query;
    Operation queryResponse;

    // Output to client
    Map<String, ServerAddress> candidates;
    Throwable exception;

  }

  @Inject
  public CloudStoreConstraintChecker(XenonRestClient xenonRestClient, CloudStoreHelper cloudStoreHelper) {
    this.xenonRestClient = xenonRestClient;
    this.cloudStoreHelper = cloudStoreHelper;

    try {
      // We need a Xenon client to send requests to CloudStore. You might ask why we create one
      // 1. We don't use XenonRestClient because it's blocking, and we want to be asynchronous
      // 2. We don't use ServiceHost.sendRequest() because it's not present in all of our tests.
      xenonClient = (NettyHttpServiceClient) NettyHttpServiceClient.create(
          XenonRestClient.class.getCanonicalName(),
          Executors.newFixedThreadPool(1),
          Executors.newScheduledThreadPool(0));
      xenonClient.start();
    } catch (URISyntaxException uriSyntaxException) {
      logger.error("CloudStoreConstraintChecker: URISyntaxException={}", uriSyntaxException.toString());
      throw new RuntimeException(uriSyntaxException);
    }

  }

  /**
   * Synchronous interface to getCandidates().
   * This should *only* be used by tests, not production code
   */
  public Map<String, ServerAddress> getCandidatesSync(
      List<ResourceConstraint> resourceConstraints,
      int numCandidates) {

    List<Map<String, ServerAddress>> candidates = new ArrayList<Map<String, ServerAddress>>();

    CountDownLatch latch = new CountDownLatch(1);
    getCandidates(resourceConstraints,
        numCandidates,
        "test-request",
        (c, ex) -> {
          candidates.add(c);
          latch.countDown();
        });

    boolean done = false;

    while (!done) {
      try {
        latch.await();
        done = true;
      } catch (InterruptedException ex) {
        // Thread was interrupted, retry await()
      }
    }
    return candidates.get(0);
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
  public void getCandidates(
      List<ResourceConstraint> resourceConstraints,
      int numCandidates,
      String requestId,
      GetCandidatesCompletion completion) {

    State state = new State();
    state.currentStep = Step.VALIDATE_INPUT;
    state.resourceConstraints = resourceConstraints;
    state.numCandidates = numCandidates;
    state.requestId = requestId;
    state.completion = completion;
    state.candidates = new HashMap<>();
    getCandidates_HandleStep(state);
  }

  private void getCandidates_HandleStep(State state) {
    switch (state.currentStep) {
      case VALIDATE_INPUT:
        getCandidates_ValidateInput(state);
        break;
      case CALCULATE_RANGES:
        getCandidates_CalculateRanges(state);
        break;
      case BUILD_QUERY:
        getCandidates_BuildQuery(state);
        break;
      case GET_CANDIDATES:
        getCandidates_GetCandidates(state);
        break;
      case ANALYZE_CANDIDATES:
        getCandidates_AnalyzeCandidates(state);
        break;
      case SUCCESS:
        getCandidates_Success(state);
        break;
      case FAIL:
        getCandidates_Fail(state);
        break;
    }
  }

  private void getCandidates_ValidateInput(State state) {
    if (state.numCandidates <= 0) {
      state.exception = new IllegalArgumentException(
          "getCandidates called with invalid numCandidates: " + state.numCandidates);
      state.currentStep = Step.FAIL;
    } else {
      state.currentStep = Step.CALCULATE_RANGES;
    }
    getCandidates_HandleStep(state);
  }

  private void getCandidates_CalculateRanges(State state) {
    state.ranges = new Range[2];
    state.ranges[0] = new Range();
    state.ranges[1] = new Range();

    // Divide the hosts into two groups, based on a random midpoint
    int randomMidpoint = 1 + random.nextInt(HostService.MAX_SCHEDULING_CONSTANT - 1);
    if (random.nextBoolean()) {
      // Case 1: first try [randomMidpoint, 10000], then [0, randomMidpoint]
      state.ranges[0].lowerBound = randomMidpoint;
      state.ranges[0].upperBound = HostService.MAX_SCHEDULING_CONSTANT;
      state.ranges[0].sortOrder = SortOrder.ASC;
      state.ranges[1].lowerBound = 0;
      state.ranges[1].upperBound = randomMidpoint;
      state.ranges[1].sortOrder = SortOrder.DESC;
    } else {
      // Case 2: first try [0, randomMidpoint] then [randomMidpoint, 10000]
      state.ranges[0].lowerBound = 0;
      state.ranges[0].upperBound = randomMidpoint;
      state.ranges[0].sortOrder = SortOrder.DESC;
      state.ranges[1].lowerBound = randomMidpoint;
      state.ranges[1].upperBound = HostService.MAX_SCHEDULING_CONSTANT;
      state.ranges[1].sortOrder = SortOrder.ASC;
    }
    state.currentRange = 0;
    state.currentStep = Step.BUILD_QUERY;
    getCandidates_HandleStep(state);
  }

  /**
   * Build the query that we'll use to query CloudStore. We translate each constraint to a clause in a Xenon query.
   */
  private void getCandidates_BuildQuery(State state) {
    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(HostService.State.class)
        .addRangeClause(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
            QueryTask.NumericRange.createLongRange(
                (long) state.ranges[state.currentRange].lowerBound,
                (long) state.ranges[state.currentRange].upperBound,
                true, false));

    // Ensure that we only look for hosts that are ready (not, for example, suspended)
    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_STATE, HostState.READY);

    // Ensure that we only look for hosts that are responsive. Those are hosts with agents that respond
    // to pings and are marked as active.
    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_AGENT_STATE, AgentState.ACTIVE);

    if (state.resourceConstraints != null) {
      for (ResourceConstraint constraint : state.resourceConstraints) {

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
            try {
              addDatastoreTagClause(queryBuilder, constraint);
            } catch (NoSuchResourceException ex) {
              state.currentStep = Step.FAIL;
              state.exception = ex;
              getCandidates_HandleStep(state);
              return;
            }
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

    state.query = queryBuilder.build();
    state.currentStep = Step.GET_CANDIDATES;
    getCandidates_HandleStep(state);
  }

  /**
   * Submit the query to CloudStore.
   */
  private void getCandidates_GetCandidates(State state) {

    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(state.query)
        .setResultLimit(state.numCandidates)
        .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    if (state.ranges[state.currentRange].sortOrder == SortOrder.ASC) {
      queryTaskBuilder.orderAscending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
          ServiceDocumentDescription.TypeName.LONG);
    } else {
      queryTaskBuilder.orderDescending(HostService.State.FIELD_NAME_SCHEDULING_CONSTANT,
          ServiceDocumentDescription.TypeName.LONG);
    }

    QueryTask queryTask = queryTaskBuilder.build();

    Operation queryOperation = this.cloudStoreHelper.createPost(ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setContextId(LoggingUtils.getRequestId())
        .setCompletion((response, ex) -> {
          if (ex != null) {
            state.exception = ex;
            state.currentStep = Step.FAIL;
            getCandidates_HandleStep(state);
            return;
          }
          state.queryResponse = response;
          state.currentStep = Step.ANALYZE_CANDIDATES;
          getCandidates_HandleStep(state);
        });
    xenonClient.send(queryOperation);
  }

  private void getCandidates_AnalyzeCandidates(State state) {

    Map<String, Object> documents = extractDocumentsFromQuery(state.queryResponse);
    // Note that if documents == null, logging happened in extractDocumentsFromQuery.

    if (documents != null) {
      for (Object document : documents.values()) {
        HostService.State host = Utils.fromJson(document, HostService.State.class);
        if (host == null) {
          logger.warn("Query result had invalid host document, ignoring");
          continue;
        }
        state.candidates.put(ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink),
            new ServerAddress(host.hostAddress, host.agentPort));

        if (state.candidates.size() >= state.numCandidates) {
          // If we're searching the second half of the search space, we need to make sure not to add too many
          // candidates.
          break;
        }
      }
    }

    // If we've either searched all the ranges or we've found enough candidates,
    // we're done and we can return what we've found
    if (state.currentRange >= state.ranges.length - 1
        || state.candidates.size() >= state.numCandidates) {
      state.currentStep = Step.SUCCESS;
      getCandidates_HandleStep(state);
      return;
    }

    // We didn't find enough queries, check the next range
    state.currentRange++;
    updateQueryRange(state.query,
        state.ranges[state.currentRange].lowerBound,
        state.ranges[state.currentRange].upperBound);
    state.currentStep = Step.GET_CANDIDATES;
    getCandidates_HandleStep(state);
    return;
  }

  private void getCandidates_Success(State state) {
    state.completion.handle(state.candidates, null);
    return;
  }

  private void getCandidates_Fail(State state) {
    state.candidates.clear();
    state.completion.handle(state.candidates, state.exception);
    return;
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

  /**
   * Figure out the clause to add to our query to only pick hosts that have datastores with a given tag.
   *
   * Unfortunately, to do this correctly we need to do a query to find the datastores with the tag.
   */
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

    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DatastoreService.State.class)
            .addClause(tagBuilder.build())
            .build())
        .setResultLimit(1000);

    QueryTask queryTask = queryTaskBuilder.build();


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
