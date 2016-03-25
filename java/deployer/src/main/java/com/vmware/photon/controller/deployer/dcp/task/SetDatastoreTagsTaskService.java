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

import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP micro-service that sets tags on datastores.
 */
public class SetDatastoreTagsTaskService extends StatefulService {

  /**
   * Class defines the state of the SetDatastoreTagsTaskService.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    @Immutable
    public List<String> tagsToAdd;

    @Immutable
    public List<String> tagsToRemove;

    /**
     * If left as null, it means check typeToMatch and apply to all.
     * Otherwise, applies to all the datastores with ids in this list.
     * If this field is entered, typeToMatch needs to be empty
     */
    @Immutable
    public List<String> idsToMatch;

    /**
     * Type of the datastores to apply the tags.
     * Either idsToMatch or this field needs to be filled.
     * If both left empty, applys to all.
     */
    @Immutable
    public String typeToMatch;
  }

  public SetDatastoreTagsTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param startOperation
   */
  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on
   * the current service instance.
   *
   * @param patch The patch operation.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        queryDatastores(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies current state object.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if ((currentState.tagsToAdd == null || currentState.tagsToAdd.size() == 0)
        && (currentState.tagsToRemove == null || currentState.tagsToRemove.size() == 0)) {
      throw new IllegalArgumentException(
          "tagsToAdd and tagsToRemove parameters are both empty");
    }

    if (currentState.idsToMatch != null && currentState.idsToMatch.size() > 0
        && currentState.typeToMatch != null && currentState.typeToMatch.trim().length() > 0) {
      throw new IllegalArgumentException(
          "idsToMatch and typeToMatch parameters cannot be supplied at the same time");
    }
  }

  /**
   * Checks a patch object for validity against a document state object.
   *
   * @param startState Start state object.
   * @param patchState Patch state object.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * Applies a patch to a service document and returns the update document state.
   *
   * @param startState
   * @param patchState
   * @return
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * transition to a new state.
   *
   * @param stage The state to which the service instance should be transitioned.
   */
  private void sendStageProgressPatch(TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "Sending stage progress path %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }


  /**
   * This method builds a state object that is used to submit a stage process self-patch.
   *
   * @param stage The state to which the service instance is to be transitioned.
   * @param e     (Optional) Representing the failure encountered by the service instance.
   * @return A state object to be used for stage process self-patch.
   */
  private State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  /**
   * Send a patch to the current instance to put it to FAILED state.
   *
   * @param e The failure exception that causes this state transition.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * Get the datastores.
   *
   * @param currentState
   * @throws Throwable
   */
  private void queryDatastores(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();

    if (currentState.idsToMatch != null && currentState.idsToMatch.size() > 0) {
      addIdsQuery(querySpecification, currentState);
      querySpecification.query.addBooleanClause(kindClause);
    } else if (currentState.typeToMatch != null && currentState.typeToMatch.trim().length() > 0) {
      addTypesQuery(querySpecification, currentState);
      querySpecification.query.addBooleanClause(kindClause);
    } else {
      querySpecification.query = kindClause;
    }

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(SetDatastoreTagsTaskService.this, documentLinks);
                    if (documentLinks.size() == 0) {
                      throw new IllegalStateException("Could not find a datastore to match the query.");
                    }

                    final AtomicInteger latch = new AtomicInteger(documentLinks.size());

                    for (String datastoreServiceLink : documentLinks) {
                      getDatastore(currentState, datastoreServiceLink, latch);
                    }

                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void addIdsQuery(QueryTask.QuerySpecification querySpecification, State currentState) {
    // Get Ids query
    QueryTask.Query idsClause = new QueryTask.Query();
    for (int i = 0; i < currentState.idsToMatch.size(); i++) {
      String id = currentState.idsToMatch.get(i);
      ServiceUtils.logInfo(this, "Datastore query id %d %s", i, id);

      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(DatastoreService.State.FIELD_NAME_ID)
          .setTermMatchValue(id);
      if (currentState.idsToMatch.size() == 1) {
        idsClause = idClause;
      } else {
        idClause.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
        idsClause.addBooleanClause(idClause);
      }
    }
    querySpecification.query.addBooleanClause(idsClause);
  }

  private void addTypesQuery(QueryTask.QuerySpecification querySpecification, State currentState) {
    // Get types to match query
    ServiceUtils.logInfo(this, "Datastore query typeMatch %s", currentState.typeToMatch);

    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(DatastoreService.State.FIELD_NAME_TYPE)
        .setTermMatchValue(currentState.typeToMatch);
    querySpecification.query.addBooleanClause(typeClause);
  }

  private void getDatastore(final State currentState, final String datastoreServiceLink, AtomicInteger latch) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(datastoreServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DatastoreService.State datastoreState = completedOp.getBody(DatastoreService.State.class);
                    Set<String> tags = calculateTags(currentState, datastoreState);
                    setTagsOnDatastore(currentState, datastoreServiceLink, tags, latch);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private Set<String> calculateTags(final State currentState, final DatastoreService.State datastoreState) {
    Set<String> currentTags = datastoreState.tags;
    HashSet<String> result = new HashSet<String>();
    if (currentState.tagsToRemove != null && currentTags != null) {
      for (String tag : currentTags) {
        boolean foundInTagsToRemove = false;
        for (String removeTag : currentState.tagsToRemove) {
          if (tag.equalsIgnoreCase(removeTag)) {
            foundInTagsToRemove = true;
            break;
          }
        }

        if (!foundInTagsToRemove) {
          result.add(tag);
        }
      }
    }

    if (currentState.tagsToAdd != null) {
      boolean foundInTagsToRemove = false;
      for (String addTag : currentState.tagsToAdd) {
        boolean foundInExistingTags = false;
        if (currentTags != null) {
          for (String tag : currentTags) {
            if (tag.equalsIgnoreCase(addTag)) {
              foundInExistingTags = true;
              break;
            }
          }
        }
        if (!foundInExistingTags) {
          result.add(addTag);
        }
      }
    }
    return result;
  }

  private void setTagsOnDatastore(final State currentState, final String datastoreServiceLink, Set<String> tags,
                                  AtomicInteger latch) {

    DatastoreService.State datastorePatch = new DatastoreService.State();
    datastorePatch.tags = tags;

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(datastoreServiceLink)
            .setBody(datastorePatch)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  if (latch.decrementAndGet() == 0) {
                    sendStageProgressPatch(TaskState.TaskStage.FINISHED);
                  }
                }
            ));
  }
}
