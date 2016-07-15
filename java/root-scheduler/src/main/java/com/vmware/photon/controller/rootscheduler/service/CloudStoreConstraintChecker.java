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
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.exceptions.ConstraintMatchingDatastoreNotFoundException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * This scheduler constraint checker (currently the only one used by the scheduler) will find a set of up to
 * numCandidates random hosts that match the by given constraints querying Cloudstore.
 *
 * Note that this is used from within the PlacementTaskService. It is asynchronous, but it doesn't update the
 * state of the task with PATCHs. This is partly because it doesn't need to (the task is neither persisted
 * nor replicated) and partly because it keeps the constraint checker distinctd from the placement task. This
 * will allow us to do easy future experimentation with alternative constraint checkers.
 *
 * The approach here is interesting, because we want random hosts, but Xenon's  LuceneDocumentQueryService
 * cannot select randomly. To get randomness, each HostService document is assigned a random number between
 *  0 and 10,000 when it's created. We pick a random number and try to find hosts close to that
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
 *
 * There are two entry points into this class that are used:
 *
 * getCandidates(): This is the entry point for all production code. It runs asynchronously and returns
 * the results via a completion routine.
 *
 * getCandidatesSync(): This is meant for tests. It exists because originally all of the code was synchronous
 * and all the tests assumed the results were returned synchronously. When we updated the code to be asynchronous,
 * we made a synchronous interface to keep the tests straightforward.
 *
 * A note on logging and tracing the request IDs:
 * - This code is used from within a Xenon service, so we should use the ServiceUtils functions for logging.
 *   We don't, because it's also tested from code that doesn't use a Xenon service.
 * - In order to get the request ID in the logging and preserve the request ID for the PlacementTask (which will
 *   call thrift, which passes the request ID to the host in the tracing_info), the code sets the request ID
 *   in the MDC (via LoggigUtils.setRequestId) in our Xenon callbacks.
 */
public class CloudStoreConstraintChecker implements ConstraintChecker {

  private static final String HOST_SELF_LINK_PREFIX = HostServiceFactory.SELF_LINK + "/";
  private static final List<String> managementTagValues = Arrays.asList(UsageTag.MGMT.name());

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintChecker.class);

  private final Random random = new Random();

  private final CloudStoreHelper cloudStoreHelper;
  private NettyHttpServiceClient xenonClient;

  private class Range {
    int lowerBound;
    int upperBound;
    SortOrder sortOrder;
  }

  // The steps in our asynchronous process. Documentation for each step is provided with the function that
  // corresponds to each step.
  private enum Step {
    VALIDATE_INPUT,
    EXTRACT_DATASTORE_TAG_CONSTRAINTS,
    DATASTORE_TAG_QUERY,
    BUILD_DS_TAG_CONSTRAINT,
    CALCULATE_RANGES,
    BUILD_QUERY,
    GET_CANDIDATES,
    ANALYZE_CANDIDATES,
    SUCCESS,
    FAIL
  }

  // The internal state we maintain as we proceed through the process of finding candidates
  private class State {

    // Input from client: details of resource (VM or disk) constraints (e.g. dastore affinities)
    List<ResourceConstraint> resourceConstraints;
    // Input from client: desired number of candidates to be found
    int numCandidates;
    // Input from client: completion to call when we're all done
    GetCandidatesCompletion completion;

    // Current place in our process
    Step currentStep;
    // Each range is a section of the scheduling space (see above). Right now we only have two ranges,
    // but this is an array both for simplicity of implementation and because we could have more range
    // in the future
    Range ranges[];
    // Index into ranges
    int currentRange;

    // Before we search for candidates, we resolve any datastore tag constraints.
    // This is the response to the query for datastores corresponding to the tags
    Operation datastoreTagResponse;
    // The list of datastore tag constraints, extracted from the resource constraints.
    List<ResourceConstraint> dsTagConstraints;
    // extraDatastoreConstraints are the constraints we add based on the datastore tag constraints
    // We would prefer to add them to resourceConstraints, but it's immutable, so we use a separate list
    List<ResourceConstraint> extraDatastoreConstraints;

    // The query we'll make to find the resource candidates
    QueryTask.Query query;
    // The response we got from the query
    Operation queryResponse;

    // The set of candidates we found; will be returned from the completion
    Map<String, ServerAddress> candidates;
    // If we failed and generated an exception, this is in. It will be returned from the completion
    Throwable exception;

  }

  public CloudStoreConstraintChecker(CloudStoreHelper cloudStoreHelper) {
    this.cloudStoreHelper = cloudStoreHelper;

    try {
      // We need a Xenon client to send requests to CloudStore. You might ask why we create one.
      // 1. We don't use XenonRestClient because it's blocking, and we want to be asynchronous
      // 2. We don't use ServiceHost.sendRequest() because the ServiceHost is not present in all of our tests.
      // We could work around this by having the tests pass the CloudStore host. Doing that ran into
      // Guice injection issues, so we're going to stick with the xenonClient issue. When we remove Guice,
      // we should revisit this by passing in the host.
      xenonClient = (NettyHttpServiceClient) NettyHttpServiceClient.create(
          CloudStoreConstraintChecker.class.getCanonicalName(),
          Executors.newFixedThreadPool(4),
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
   * The main entry point to find candidates for the PlacementTask.
   *
   * See the Javadoc on the class for an introduction to the approach.
   *
   * Note that the result of this is shared via the completion routine, not the return code: it's
   * fully asynchronous.
   */
  @Override
  public void getCandidates(
      List<ResourceConstraint> resourceConstraints,
      int numCandidates,
      GetCandidatesCompletion completion) {

    State state = new State();
    state.currentStep = Step.VALIDATE_INPUT;
    state.resourceConstraints = resourceConstraints;
    state.extraDatastoreConstraints = new ArrayList<>();
    state.numCandidates = numCandidates;
    state.completion = completion;
    state.candidates = new HashMap<>();
    getCandidates_HandleStep(state);
  }

  /**
   * The state machine for getCandidates().
   *
   * Strictly speaking, this is just syntactic sugar to make the flow easier.
   * The getCandidates() process is asynchronous: we make a series of async calls,
   * and get the results via completions. We could simply chain together completions,
   * and it would flow correctly.
   *
   * Instead, each completion calls back to this method, which then selects the next
   * method to run. The goal here is to make the overall flow clear.
   *
   * By and large, we proceed sequentially through this state machine. There are a few exceptions:
   * 1. If there are no datastore tag constraints, we don't need to build datastore constraints based on them
   * 2. We break the search space into ranges. If the first range doesn't yield enough hosts, we iterate
   * 3. At any time we can proceed to the FAIL state.
   *
   * @param state
   */
  private void getCandidates_HandleStep(State state) {
    try {
      switch (state.currentStep) {
        case VALIDATE_INPUT:
          getCandidates_ValidateInput(state);
          break;
        case EXTRACT_DATASTORE_TAG_CONSTRAINTS:
          getCandidates_ExtractDatastoreTagConstraints(state);
          break;
        case DATASTORE_TAG_QUERY:
          getCandidates_DatastoreTagQuery(state);
          break;
        case BUILD_DS_TAG_CONSTRAINT:
          getCandidates_BuildDsTagConstraint(state);
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
    } catch (Exception ex) {
      state.exception = ex;
      getCandidates_Fail(state);
    }
  }

  /**
   * Verify that the input from the client is okay.
   */
  private void getCandidates_ValidateInput(State state) {
    if (state.numCandidates <= 0) {
      state.exception = new IllegalArgumentException(
          "getCandidates called with invalid numCandidates: " + state.numCandidates);
      state.currentStep = Step.FAIL;
    } else {
      state.currentStep = Step.EXTRACT_DATASTORE_TAG_CONSTRAINTS;
    }
    getCandidates_HandleStep(state);
  }

  /**
   *
   * Scan through the set of resource constraints and extract all of the datastore tag constraints.
   * Because we cannot directly query for datastore tag, we'll convert these in the next steps to
   * datastore constraints.
   */
  private void getCandidates_ExtractDatastoreTagConstraints(State state) {
    state.dsTagConstraints = new ArrayList<>();

    if (state.resourceConstraints != null) {
      for (ResourceConstraint constraint : state.resourceConstraints) {
        if (constraint.getType().equals(ResourceConstraintType.DATASTORE_TAG)) {

          List<String> tags = constraint.getValues();
          if (tags != null && !tags.isEmpty()) {
            state.dsTagConstraints.add(constraint);
          }
        }
      }
    }

    // We'll only resolve the datastore tag constraints if we have some.
    // Otherwise we proceed onwards with the main query
    if (state.dsTagConstraints.isEmpty()) {
      state.currentStep = Step.CALCULATE_RANGES;
    } else {
      state.currentStep = Step.DATASTORE_TAG_QUERY;
    }
    getCandidates_HandleStep(state);
  }

  /**
   * Pick one of our datastore tag constraints and query for the datastores that fulfill it.
   */
  private void getCandidates_DatastoreTagQuery(State state) {
    ResourceConstraint tagConstraint = state.dsTagConstraints.get(0);
    List<String> tags = tagConstraint.getValues();

    QueryTask.Query.Builder tagBuilder = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.MUST_OCCUR);
    for (String tag : tags) {
      tagBuilder.addCollectionItemClause(
          DatastoreService.State.FIELD_NAME_TAGS, tag, QueryTask.Query.Occurance.SHOULD_OCCUR);
    }

    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DatastoreService.State.class)
            .addClause(tagBuilder.build())
            .build());
    QueryTask queryTask = queryTaskBuilder.build();

    Operation queryOperation = this.cloudStoreHelper.createPost(ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setContextId(LoggingUtils.getRequestId())
        .setCompletion((response, ex) -> {
          // See note on logging, above
          LoggingUtils.setRequestId(response.getContextId());
          if (ex != null) {
            state.exception = ex;
            state.currentStep = Step.FAIL;
            getCandidates_HandleStep(state);
            return;
          }
          state.datastoreTagResponse = response;
          state.currentStep = Step.BUILD_DS_TAG_CONSTRAINT;
          getCandidates_HandleStep(state);
        });
    xenonClient.send(queryOperation);
  }

  /**
   * Based on the result of our datastore query in getCandidates_DatastoreTagQuery(),
   * build a constraint on the datastores we can use.
   *
   * Note: if there are datastore tag constraints remaining, we'll go back to DATASTORE_TAG_QUERY,
   * otherwise we proceed.
   */
  private void getCandidates_BuildDsTagConstraint(State state) {
    ResourceConstraint tagConstraint = state.dsTagConstraints.get(0);
    ServiceDocumentQueryResult queryResult = state.datastoreTagResponse.getBody(QueryTask.class).results;

    List<String> datastoreIds = new ArrayList<>();
    for (String documentLink : queryResult.documentLinks) {
      datastoreIds.add(ServiceUtils.getIDFromDocumentSelfLink(documentLink));
    }

    // No datastore which match the specified constraint is available
    if (datastoreIds.size() == 0) {
      state.exception = new ConstraintMatchingDatastoreNotFoundException(
          "Cannot satisfy constraint for datastore tag(s) '" + tagConstraint.getValues().toString() + "' found");
      state.currentStep = Step.FAIL;
      getCandidates_HandleStep(state);
      return;
    }

    // Based on the datastores we got, build a new clause to select the datastores.
    ResourceConstraint datastoreConstraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, datastoreIds);
    if (tagConstraint.isSetNegative() && tagConstraint.isNegative()) {
      datastoreConstraint.setNegative(true);
    }
    state.extraDatastoreConstraints.add(datastoreConstraint);
    logger.info("Adding datastore constraint to fulfill datastore tag constraint: {}",
        Utils.toJson(false, false, datastoreConstraint));

    // Remove the datastore tag constraint we just considered
    state.dsTagConstraints.remove(0);
    state.datastoreTagResponse = null;

    // If we have more datastore tag constraints, handle them
    if (!state.dsTagConstraints.isEmpty()) {
      state.currentStep = Step.DATASTORE_TAG_QUERY;
      getCandidates_HandleStep(state);
      return;
    }

    // No more datastore tag constraints, so proceed
    state.currentStep = Step.CALCULATE_RANGES;
    getCandidates_HandleStep(state);
  }

  /**
   * Set up the scheduling space ranges we'll use in the query
   *
   * As noted above, we divide the scheduling space into two ranges. This calculates those ranges.
   *
   * If needed, we could break the space into smaller ranges in the future.
   *
   */
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
   * Build the query that we'll use to query CloudStore for all the candidate hosts that meet
   * the resource constraints and are within the selected range in the scheduling space.
   *
   * We translate each constraint to a clause in a Xenon query task
   *
   * Note that this could be executed once for each range, if we don't find enough candidates.
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

    // Check if management only constraint is present. If not query for only the cloud hosts. This is needed to make
    // sure that we place all VMs which do not have a MANAGEMENT_ONLY constraint only on the hosts which are tagged
    // with CLOUD usage tag.
    if (state.resourceConstraints == null || !(state.resourceConstraints.stream()
        .filter(rc -> rc.getType() == ResourceConstraintType.MANAGEMENT_ONLY)
        .findFirst()).isPresent()) {
      queryBuilder.addCollectionItemClause(HostService.State.FIELD_NAME_USAGE_TAGS, UsageTag.CLOUD.name());
    }


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
            // Nothing to do here: we handled it EXTRACT_DATASTORE_TAG_CONSTRAINTS and will add
            // extra datastore constraints based on it below.
            break;
          case HOST:
            addFieldClause(queryBuilder, HostService.State.FIELD_NAME_SELF_LINK, HOST_SELF_LINK_PREFIX, constraint);
            break;
          case MANAGEMENT_ONLY:
            // This constraint doesn't come with values, but we want to reuse our code, so we set them
            constraint.setValues(managementTagValues);
            addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_USAGE_TAGS, constraint);
            break;
          case NETWORK:
            addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_REPORTED_NETWORKS, constraint);
            break;
          case VIRTUAL_NETWORK:
            // Nothing needs to be done here: In virtual network case, all hosts are already wired together.
            // In other words, a VM on any host can join any virtual network. So this constraint should be ignored.
            break;
          default:
            throw new IllegalStateException("Invalid resource constraint: " + constraint);
        }
      }
    }
    if (state.extraDatastoreConstraints != null) {
      for (ResourceConstraint constraint : state.extraDatastoreConstraints) {
        addCollectionItemClause(queryBuilder, HostService.State.FIELD_NAME_REPORTED_DATASTORES, constraint);
      }
    }

    state.query = queryBuilder.build();
    state.currentStep = Step.GET_CANDIDATES;
    getCandidates_HandleStep(state);
  }

  /**
   * Submit the query that we built in getCandidates_BuildQuery(). This query will result in 0 to numCandidates
   * hosts.
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
          // See note on logging, above
          LoggingUtils.setRequestId(response.getContextId());
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

    if (QueryTaskUtils.queryClausesContainProperty(state.query, HostService.State.FIELD_NAME_AVAILABILITY_ZONE_ID)) {
      logger.info("GetCandidates query: {}", OperationUtils.createLogMessageWithCompleteInformation(queryOperation));
    }
  }

  /**
   * Extract the hosts we found and decide if we need to keep searching or can stop.
   */
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

  /**
   * We were successful, provide the caller with the set of host candidates.
   * Note that "successful" means we didn't encounter any fatal errors: there may
   * be zero candidates.
   */
  private void getCandidates_Success(State state) {
    logger.info("Found {} candidate(s): {}", state.candidates.size(),
        Utils.toJson(false, false, state.candidates.values()));
    state.completion.handle(state.candidates, null);
    return;
  }

  /**
   * We encountered some fatal error: inform the client of the exception indicating the problem.
   */
  private void getCandidates_Fail(State state) {
    logger.warn("getCandidates() failed: " + state.exception);
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
}
