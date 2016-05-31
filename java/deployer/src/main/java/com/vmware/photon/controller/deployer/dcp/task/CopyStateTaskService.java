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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreServiceGroup;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultString;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.util.Pair;
import com.vmware.xenon.common.AuthenticationUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.base.Objects;

import javax.annotation.Nullable;

import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * This class moves DCP state between two DCP clusters.
 */
public class CopyStateTaskService extends StatefulService {

  private static final String DOCUMENT_UPDATE_TIME_MICROS = "documentUpdateTimeMicros";

  /**
   * This class defines the document state associated with a single
   * {@link CopyStateTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_SOURCE_FACTORY_LINK = "sourceFactoryLink";
    public static final String FIELD_NAME_FACTORY_LINK = "factoryLink";
    public static final String FIELD_NAME_SOURCE_PROTOCOL = "sourceProtocol";
    public static final String FIELD_NAME_DESTINATION_PROTOCOL = "destinationProtocol";
    public static final String FIELD_NAME_DESTINATION_PORT = "destinationPort";
    public static final String FIELD_NAME_DESTINATION_IP = "destinationIp";

    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    @Immutable
    @NotNull
    public Set<Pair<String, Integer>> sourceServers;

    @Immutable
    @DefaultString(value = "http")
    public String sourceProtocol;

    @Immutable
    @NotNull
    public String destinationIp;

    @Immutable
    @NotNull
    public Integer destinationPort;

    @Immutable
    @DefaultString(value = "http")
    public String destinationProtocol;

    // Destination factory link
    @Immutable
    @NotNull
    public String factoryLink;

    @Immutable
    @NotNull
    public String sourceFactoryLink;

    @Immutable
    @DefaultString(value = "taskState.stage")
    public String taskStateFieldName;

    @Immutable
    @DefaultInteger(value = 500)
    public Integer queryResultLimit;

    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    @WriteOnce
    public String destinationServiceClassName;

    @Immutable
    @DefaultLong(value = 0)
    public Long queryDocumentsChangedSinceEpoc;

    @WriteOnce
    public Long lastDocumentUpdateTimeEpoc;

    @Immutable
    @DefaultBoolean(value = false)
    public Boolean performHostTransformation;
  }

  public CopyStateTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (!startState.factoryLink.endsWith("/")) {
      startState.factoryLink += "/";
    }

    if (!startState.sourceFactoryLink.endsWith("/")) {
      startState.sourceFactoryLink += "/";
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
      ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      return;
    }

    try {
      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        String destinationTemplate = findDestinationServiceClassName(startState);
        State patchState = buildPatch(TaskState.TaskStage.STARTED, null);
        if (destinationTemplate != null) {
          patchState.destinationServiceClassName = destinationTemplate;
        }
        sendStageProgressPatch(patchState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOperation.complete();

    if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
      ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      return;
    }
    if (currentState.taskState.stage != TaskState.TaskStage.STARTED) {
      return;
    }
    try {
      LinkedBlockingQueue<Pair<String, Integer>> queue = new LinkedBlockingQueue<>(currentState.sourceServers);
      retrieveDocuments(currentState, queue, currentState.queryDocumentsChangedSinceEpoc);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void retrieveDocuments(
      final State currentState,
      Queue<Pair<String, Integer>> sourceServers,
      long lastUpdateQueryTime) {

    OperationJoin.create(
        currentState.sourceServers.stream()
          .map(pair -> {
            Operation op = Operation
                .createPost(buildQueryURI(currentState, pair))
                .setBody(retrieveDocumentsQuery(currentState));
            AuthenticationUtils.addSystemUserAuthcontext(op, getSystemAuthorizationContext());
            return op;
          })
        ).setCompletion((os, ts) -> {
          if (ts != null && !ts.isEmpty()) {
            failTask(ts);
            return;
          }
          Map<URI, String> nextPageLinks = os.values().stream()
              .filter(o -> o.getBody(QueryTask.class).results.nextPageLink != null)
              .map(o -> {
                QueryTask qt = o.getBody(QueryTask.class);
                URI uri = convertToBaseUri(currentState, o);
                return new AbstractMap.SimpleEntry<>(uri, qt.results.nextPageLink);
            }).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
          continueWithNextPage(nextPageLinks, currentState, lastUpdateQueryTime);
        })
      .sendWith(this);
  }

  private void continueWithNextPage(
      Map<URI, String> nextPageLinks,
      State currentState,
      long lastUpdateQueryTime) {

    if (!nextPageLinks.isEmpty()) {
      retrieveNextPage(currentState, nextPageLinks, lastUpdateQueryTime);
    } else {
      // if there are no more results in the querye continue with the next host
      State patch = new State();
      patch.taskState = new TaskState();
      patch.taskState.stage = TaskState.TaskStage.FINISHED;
      patch.lastDocumentUpdateTimeEpoc = lastUpdateQueryTime;
      TaskUtils.sendSelfPatch(CopyStateTaskService.this, patch);
    }
  }

  private void retrieveNextPage(
      final State currentState,
      Map<URI, String> nextPageLinks,
      long lastUpdateQueryTime) {

    OperationJoin.create(
        nextPageLinks.entrySet().stream()
          .map(entry -> {
            Operation o = Operation.createGet(UriUtils.buildUri(entry.getKey(), entry.getValue()));
            AuthenticationUtils.addSystemUserAuthcontext(o, getSystemAuthorizationContext());
            return o;
          })
        )
      .setCompletion((os, ts) -> {
        if (ts != null && ts.isEmpty()) {
          failTask(ts);
          return;
        }
        Map<URI, ServiceDocumentQueryResult> results = os.values().stream()
          .map(o -> {
            QueryTask qt =  o.getBody(QueryTask.class);
            return new AbstractMap.SimpleEntry<>(convertToBaseUri(currentState, o), qt.results);
          })
          .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        storeDocuments(currentState, results, lastUpdateQueryTime);
      })
      .sendWith(this);
  }

  private void storeDocuments(
      final State currentState,
      Map<URI, ServiceDocumentQueryResult> results,
      long lastUpdateQueryTime) {

    if (results.isEmpty()) {
      continueWithNextPage(
          Collections.emptyMap(),
          currentState,
          lastUpdateQueryTime);
      return;
    }

    URI destinationFactoryURI = buildDestinationFactoryURI(currentState);

    List<Object> ownerSelectedResults = new ArrayList<>();
    for (ServiceDocumentQueryResult result : results.values()) {
      ownerSelectedResults.addAll(
          result.documents.values().stream()
          .filter(doc -> {
            ServiceDocument serviceDoc = Utils.fromJson(doc, ServiceDocument.class);
            return serviceDoc.documentOwner == null
                || Objects.equal(serviceDoc.documentOwner, result.documentOwner);
          })
          .collect(Collectors.toList())
        );
    }
    QueryTaskUtils.logQueryResults(this, ownerSelectedResults.stream()
        .map(doc -> {
          ServiceDocument serviceDoc = Utils.fromJson(doc, ServiceDocument.class);
          return serviceDoc.documentSelfLink;
        })
        .collect(Collectors.toList()));

    long lastUpdateTimeOnPage = ownerSelectedResults.stream()
        .map(doc -> Utils.getJsonMapValue(doc, DOCUMENT_UPDATE_TIME_MICROS, Long.class))
        .mapToLong(l -> l.longValue())
        .max()
        .orElse(0);
    final long newLastUpdateTime = Math.max(lastUpdateQueryTime, lastUpdateTimeOnPage);

    if (ownerSelectedResults.isEmpty()) {
      Map<URI, String> pageLinks = results.entrySet().stream()
          .filter(entry -> entry.getValue().nextPageLink != null)
          .map(entry -> {
            return new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().nextPageLink);
          })
          .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        continueWithNextPage(
            pageLinks,
            currentState,
            newLastUpdateTime);
      return;
    }

    OperationJoin
        .create(ownerSelectedResults.stream()
            .map(document -> {
              String documentId = extractId(document, currentState.sourceFactoryLink);
              return buildDeleteOperation(destinationFactoryURI + "/" + documentId);
            }))
        .setCompletion((opers, execptions) -> {
          if (null != execptions && !execptions.isEmpty()) {
            // Ignore delete not found error
          }
          OperationJoin
              .create(ownerSelectedResults.stream()
                  .map(document -> {
                    Object json = removeFactoryPathFromSelfLink(document, currentState.sourceFactoryLink);
                    return buildPostOperation(json, destinationFactoryURI, currentState);
                  }))
              .setCompletion((ops, exs) -> {
                if (null != exs && !exs.isEmpty()) {
                  failTask(exs);
                  return;
                }
                Map<URI, String> pageLinks = results.entrySet().stream()
                  .filter(entry -> entry.getValue().nextPageLink != null)
                  .map(entry -> {
                    return new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().nextPageLink);
                  })
                  .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                continueWithNextPage(
                    pageLinks,
                    currentState,
                    newLastUpdateTime);
              })
              .sendWith(this);
        })
        .sendWith(this);
  }

  private Operation buildPostOperation(Object document, URI uri, State currentState) {
    try {
      Object documentWithRenamedFields = handleRenamedFields(document, currentState);
      Operation postOp = Operation
          .createPost(uri)
          .setUri(uri)
          .setBody(documentWithRenamedFields)
          .forceRemote()
              // PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE is a workaround needed
              // because Xenon 0.7.0 does not allow POST to a previously deleted service
              // we will need to implement an alternative solution using idempotent posts so that this workaround can
              // be removed https://www.pivotaltracker.com/story/show/114425679
          .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
          .setReferer(uri);
      return postOp;
    } catch (Throwable ex) {
      failTask(ex);
      throw new RuntimeException(ex);
    }
  }

  private URI convertToBaseUri(final State currentState, Operation o) {
    URI full = o.getUri();
    URI uri = UriUtils.buildUri(currentState.sourceProtocol, full.getHost(), full.getPort(), null, null);
    return uri;
  }

  private String findDestinationServiceClassName(State currentState) {
    String destinationDocument = null;
    try {
      for (Class<?> factoryService : CloudStoreServiceGroup.FACTORY_SERVICES) {
        String factoryServiceLink = ServiceHostUtils.getServiceSelfLink("SELF_LINK", factoryService);
        if (!factoryServiceLink.endsWith("/")) {
          factoryServiceLink += "/";
        }
        if (factoryServiceLink.equals(currentState.factoryLink)) {
          FactoryService factoryInstance = (FactoryService) factoryService.newInstance();

          Service instance = factoryInstance.createServiceInstance();
          destinationDocument = instance.getClass().getCanonicalName();
          break;
        }
      }
    } catch (Throwable t) {
      // Log and ignore
      ServiceUtils.logSevere(this, "Ignored error ", t);
    }

    return destinationDocument;
  }

  private Object handleRenamedFields(Object document, State currentState) throws Throwable {
    if (!currentState.performHostTransformation) {
      return document;
    }
    Object result = document;
    if (currentState.destinationServiceClassName != null) {
      // Serialize original document into destination
      Class<?> destinationDoc = Class.forName(currentState.destinationServiceClassName);
      @SuppressWarnings("unchecked")
      Service sd = ((Class<Service>) destinationDoc).newInstance();
      ServiceDocument convertedServiceDocument = Utils.fromJson(document, sd.getStateType());
      UpgradeUtils.handleRenamedField(document, convertedServiceDocument);
      // Convert it back to json

      if (sd.getStateType() == HostService.State.class) {
        HostService.State fromJson =  (HostService.State) convertedServiceDocument;
        fromJson.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
      }
      result = Utils.toJson(convertedServiceDocument);
    }

    return result;
  }

  private QueryTask retrieveDocumentsQuery(State currentState) {
    QueryTask.Query excludeCreatedTasks
        = buildExcludeQuery(currentState.taskStateFieldName, TaskState.TaskStage.CREATED.name());
    QueryTask.Query excludeStartedTasks
        = buildExcludeQuery(currentState.taskStateFieldName, TaskState.TaskStage.STARTED.name());
    QueryTask.Query typeClause
        = buildWildCardQuery(ServiceDocument.FIELD_NAME_SELF_LINK, currentState.sourceFactoryLink + "*");
    QueryTask.Query timeClause
        = buildTimeClause(currentState.queryDocumentsChangedSinceEpoc);

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
    querySpec.resultLimit = currentState.queryResultLimit;
    querySpec.query
        .addBooleanClause(excludeCreatedTasks)
        .addBooleanClause(excludeStartedTasks)
        .addBooleanClause(typeClause)
        .addBooleanClause(timeClause);
    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return QueryTask.create(querySpec).setDirect(true);
  }

  private Operation buildDeleteOperation(String uriString) {
    URI uri = UriUtils.buildUri(uriString);
    return Operation
        .createDelete(uri)
        .setBody(new ServiceDocument())
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setReferer(uri);
  }

  private Object removeFactoryPathFromSelfLink(Object jsonObject, String factoryPath) {
    String selfLink = extractId(jsonObject, factoryPath);
    return Utils.toJson(
        Utils.setJsonProperty(jsonObject, ServiceDocument.FIELD_NAME_SELF_LINK, selfLink));
  }

  private String extractId(Object jsonObject, String factoryPath) {
    String selfLink = Utils.getJsonMapValue(jsonObject, ServiceDocument.FIELD_NAME_SELF_LINK, String.class);
    if (selfLink.startsWith(factoryPath)) {
      selfLink = selfLink.replaceFirst(factoryPath, "");
    }
    return selfLink;
  }

  private URI buildQueryURI(State currentState, Pair<String, Integer> sourceServer) {
    return UriUtils.buildUri(
        currentState.sourceProtocol,
        sourceServer.getFirst(),
        sourceServer.getSecond(),
        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
        null);
  }

  private URI buildDestinationFactoryURI(State currentState) {
    return UriUtils.buildUri(
        currentState.destinationProtocol,
        currentState.destinationIp,
        currentState.destinationPort,
        currentState.factoryLink,
        null);
  }

  private QueryTask.Query buildWildCardQuery(String property, String value) {
    return buildBaseQuery(property, value).setTermMatchType(QueryTask.QueryTerm.MatchType.WILDCARD);
  }

  private QueryTask.Query buildExcludeQuery(String property, String value) {
    QueryTask.Query excludeStarted = buildBaseQuery(property, value);
    excludeStarted.occurance = QueryTask.Query.Occurance.MUST_NOT_OCCUR;
    return excludeStarted;
  }

  private QueryTask.Query buildBaseQuery(String property, String value) {
    return new QueryTask.Query()
        .setTermPropertyName(property)
        .setTermMatchValue(value);
  }

  private QueryTask.Query buildTimeClause(Long startTimeEpoc) {
    return new QueryTask.Query()
        .setTermPropertyName(DOCUMENT_UPDATE_TIME_MICROS)
        .setNumericRange(NumericRange.createGreaterThanRange(startTimeEpoc));
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void sendStageProgressPatch(final State state) {
    ServiceUtils.logInfo(this, "Sending stage progress patch %s", state.taskState.stage);
    TaskUtils.sendSelfPatch(this, state);
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, new Exception());
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }

  private State buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
