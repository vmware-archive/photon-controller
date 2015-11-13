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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.OperationSequence;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState.TaskStage;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.util.ExceptionUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This service reports the status of the migration initialization.
 */
public class MigrationStatusUpdateTriggerService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link MigrationStatusUpdateTriggerService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the link to the deployment document.
     */
    @Immutable
    @NotNull
    public String deploymentServiceLink;
  }

  public MigrationStatusUpdateTriggerService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    ValidationUtils.validateState(startState);
    start.setBody(startState).complete();
  }

  @Override
  public void handleGet(Operation get) {
    State currentState = getState(get);
    Operation copyStateTaskQuery = generateKindQuery(CopyStateTaskService.State.class);
    Operation uploadVibTaskQuery = generateKindQuery(UploadVibTaskService.State.class);

    OperationSequence.create(copyStateTaskQuery, uploadVibTaskQuery)
      .setCompletion((op, t) -> {
        if (t != null && !t.isEmpty()) {
          ServiceUtils.logSevere(this, ExceptionUtils.createMultiException(t.values()));
          get.fail(t.get(copyStateTaskQuery.getId()));
          return;
        }

        Map<String, Integer> finishedCopyStateCounts
            = countFinishedCopyStateTaskServices(copyStateTaskQuery, op);
        List<UploadVibTaskService.State> documents
            = extractDocuments(op.get(uploadVibTaskQuery.getId()), UploadVibTaskService.State.class);
        long vibsUploaded = countTasks(documents, task -> task.taskState.stage == TaskStage.FINISHED);
        long vibsUploading = countTasks(
            documents,
            task -> task.taskState.stage == TaskStage.STARTED || task.taskState.stage == TaskStage.CREATED);

        updateDeploymentService(get, currentState, finishedCopyStateCounts, vibsUploaded, vibsUploading);
      })
      .sendWith(this);
  }

  private Map<String, Integer> countFinishedCopyStateTaskServices(
      Operation copyStateTaskQuery,
      Map<Long, Operation> op) {
    List<String> sourceFactories = HostUtils.getDeployerContext(this)
        .getFactoryLinkMapEntries().stream()
        .map(entry -> entry.getKey())
        .collect(Collectors.toList());

    List<CopyStateTaskService.State> copyStateTasks
      = extractDocuments(op.get(copyStateTaskQuery.getId()), CopyStateTaskService.State.class);

    Map<String, Integer> map = new HashMap<>();
    sourceFactories.stream().forEach(factoryLink -> map.put(appendIfNotExists(factoryLink, "/"), 0));
    copyStateTasks.stream().forEach(state -> {
      if (state.taskState.stage == TaskStage.FINISHED && map.containsKey(state.sourceFactoryLink)) {
        Integer count = map.get(state.sourceFactoryLink);
        map.put(state.sourceFactoryLink, count + 1);
      }
    });
    return map;
  }

  private void updateDeploymentService(
      Operation get,
      State currentState,
      Map<String, Integer> finishedCopyStateCounts,
      long vibsUploaded,
      long vibsUploading) {
    DeploymentService.State patch = buildPatch(finishedCopyStateCounts, vibsUploaded, vibsUploading);
    HostUtils.getCloudStoreHelper(this).patchEntity(this,
        currentState.deploymentServiceLink,
        patch,
        (operation, throwable) -> {
          if (throwable != null) {
            ServiceUtils.logSevere(this, throwable);
            get.fail(throwable);
          } else {
            get.setBody(currentState).complete();
          }
        });
  }

  private String appendIfNotExists(String factoryLink, String string) {
    if (factoryLink.endsWith(string)) {
      return factoryLink;
    }
    return factoryLink + string;
  }

  private <T> List<T> extractDocuments(Operation operation, Class<T> type) {
    Collection<Object> values = operation.getBody(QueryTask.class).results.documents.values();
    return values.stream()
        .map(entry -> Utils.fromJson(entry, type))
        .collect(Collectors.toList());
  }

  private long countTasks(List<UploadVibTaskService.State> tasks, Predicate<UploadVibTaskService.State> predicate) {
    return tasks.stream()
      .filter(predicate)
      .count();
  }

  @Override
  public void handleMaintenance(Operation maintenance) {
    maintenance.complete();
    triggerStatusCollection();
  }

  private void triggerStatusCollection() {
    Operation updateQuery = generateDataMigrationStatusUpdateTaskQuery();

    OperationJoin.JoinedCompletionHandler joinedGetCompletionHandler = (o, failures) -> {
      if (failures != null && failures.size() > 0) {
        for (Throwable failure : failures.values()) {
          ServiceUtils.logSevere(this, failure);
        }
      }
    };

    updateQuery.setCompletion((o, t) -> {
      if (t != null) {
        ServiceUtils.logSevere(this, t);
        return;
      }
      List<State> resultDocuments = QueryTaskUtils.getQueryResultDocuments(State.class, o);
      if (resultDocuments.isEmpty()) {
        return;
      }

      List<Operation> opList = new ArrayList<Operation>(resultDocuments.size());
      for (State state : resultDocuments) {
        opList.add(Operation.createGet(this, state.deploymentServiceLink));
      }

      OperationJoin join = OperationJoin.create(opList);
      join.setCompletion(joinedGetCompletionHandler);
      join.sendWith(this);
    });
    sendRequest(updateQuery);
  }

  private DeploymentService.State buildPatch(Map<String, Integer> progressMap, long vibsUploaded, long vibsUploading) {
    DeploymentService.State patch = new DeploymentService.State();
    patch.dataMigrationProgress = progressMap;
    patch.vibsUploaded = vibsUploaded;
    patch.vibsUploading = vibsUploading;
    return patch;
  }

  private Operation generateKindQuery(Class<?> clazz) {
    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(clazz));
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = typeClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return Operation
      .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
      .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  private Operation generateDataMigrationStatusUpdateTaskQuery() {
    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = typeClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return Operation
      .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
      .setBody(QueryTask.create(querySpecification).setDirect(true));
  }
}
