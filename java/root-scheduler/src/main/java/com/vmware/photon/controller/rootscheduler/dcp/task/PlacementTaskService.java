/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.rootscheduler.dcp.task;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.rootscheduler.dcp.ConstraintCheckerProvider;
import com.vmware.photon.controller.rootscheduler.dcp.ScoreCalculatorProvider;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ScoreCalculator;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon service representing a PlacementTaskService instance.
 */
public class PlacementTaskService extends StatefulService {

  public PlacementTaskService() {
    super(PlacementTask.class);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    PlacementTask startState = start.getBody(PlacementTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, t), t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    PlacementTask currentState = getState(patchOperation);
    PlacementTask patchState = patchOperation.getBody(PlacementTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStartedState(currentState);
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, t), t);
    }
  }

  private void validateStartState(PlacementTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(PlacementTask currentState, PlacementTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void processStartedState(PlacementTask currentState) {
    sendPlaceRequest(currentState);
  }

  /**
   * This method gets valid host candidates based on requested resource constraints,
   * sends a place request to each of the hosts, and selects the best candidate from
   * the responses from the candidates.
   *
   * @param currentState
   */
  private void sendPlaceRequest(PlacementTask currentState) {
    ServiceUtils.logInfo(this, "Place request: {}", currentState);
    Stopwatch watch = Stopwatch.createStarted();

    int numSamples = currentState.numSamples;
    long timeoutMs = currentState.timeoutMs;

    Stopwatch getCandidatesStopwatch = Stopwatch.createStarted();

    // Get the list of resource constraints
    List<ResourceConstraint> constraints;
    try {
      constraints = getResourceConstraints(currentState.resource);
    } catch (NoSuchResourceException ex) {
      PlacementTask patchState = buildPatch(TaskState.TaskStage.FAILED, ex);
      patchState.response = new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
      patchState.response.setError("No such resource in resource constraints");
      failTask(patchState, ex);
      return;
    } catch (SystemErrorException ex) {
      PlacementTask patchState = buildPatch(TaskState.TaskStage.FAILED, ex);
      patchState.response = new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
      patchState.response.setError("System error getting resource constraints");
      failTask(patchState, ex);
      return;
    }

    // Get all the candidates that satisfy the constraint
    ConstraintChecker checker = ((ConstraintCheckerProvider) getHost()).getConstraintChecker();
    Map<String, ServerAddress> candidates = checker.getCandidates(constraints, numSamples);
    ServiceUtils.logInfo(this, "elapsed-time flat-place-get-candidates {} milliseconds",
        getCandidatesStopwatch.elapsed(TimeUnit.MILLISECONDS));

    if (candidates.isEmpty()) {
      String msg = "Place failure, constraints cannot be satisfied for request";
      ServiceUtils.logWarning(this, "Place failure, constraints cannot be satisfied for request: {}",
          currentState.resource);
      PlacementTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      patchState.response = new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
      patchState.response.setError(msg);
      failTask(patchState, new Throwable(msg));
      return;
    }

    // Send place request to the candidates.
    ServiceUtils.logInfo(this, "Sending place requests to {} with timeout {} ms", candidates, timeoutMs);
    Stopwatch scoreCandidatesStopwatch = Stopwatch.createStarted();
    final Set<PlaceResponse> okResponses = Sets.newConcurrentHashSet();
    final Set<PlaceResultCode> returnCodes = Sets.newConcurrentHashSet();
    final CountDownLatch done = new CountDownLatch(candidates.size());
    for (Map.Entry<String, ServerAddress> entry : candidates.entrySet()) {
      ServerAddress address = entry.getValue();
      try {
        HostClient hostClient = ((HostClientProvider) getHost()).getHostClient();
        hostClient.setIpAndPort(address.getHost(), address.getPort());
        hostClient.place(currentState.resource, new AsyncMethodCallback<Host.AsyncClient.place_call>() {
          @Override
          public void onComplete(Host.AsyncClient.place_call call) {
            PlaceResponse response;
            try {
              response = call.getResult();
            } catch (TException ex) {
              onError(ex);
              return;
            }
            ServiceUtils.logInfo(PlacementTaskService.this, "Received a place response from {}: {}", entry, response);
            returnCodes.add(response.getResult());
            if (response.getResult() == PlaceResultCode.OK) {
              okResponses.add(response);
            }
            done.countDown();
          }

          @Override
          public void onError(Exception ex) {
            ServiceUtils.logWarning(PlacementTaskService.this, "Failed to get a placement response from {}: {}",
                entry, ex);
            done.countDown();
          }
        });
      } catch (RpcException ex) {
        ServiceUtils.logWarning(this, "Failed to get a placement response from {}: {}", entry, ex);
        done.countDown();
      }
    }

    // Wait for responses to come back.
    try {
      done.await(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      ServiceUtils.logTrace(this, "Got interrupted waiting for place responses", ex);
    }
    ServiceUtils.logInfo(this, "elapsed-time flat-place-score-candidates {} milliseconds",
        scoreCandidatesStopwatch.elapsed(TimeUnit.MILLISECONDS));

    // Return the best response.
    ScoreCalculator scoreCalculator = ((ScoreCalculatorProvider) getHost()).getScoreCalculator();
    PlaceResponse response = scoreCalculator.pickBestResponse(okResponses);
    watch.stop();

    PlacementTask patchState;
    if (response == null) {
      patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      // TODO(mmutsuzaki) Arbitrarily defining a precedence for return codes doesn't make sense.
      if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE);
      } else if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE);
      } else if (returnCodes.contains((PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY))) {
        response = new PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY);
      } else if (returnCodes.contains(PlaceResultCode.NO_SUCH_RESOURCE)) {
        response = new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE);
      } else if (returnCodes.contains(PlaceResultCode.INVALID_SCHEDULER)) {
        response = new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER);
      } else {
        response = new PlaceResponse(PlaceResultCode.SYSTEM_ERROR);
        String msg = String.format("Received no response in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
        response.setError(msg);
        ServiceUtils.logSevere(this, msg);
      }
    } else {
      patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
      ServiceUtils.logInfo(this, "Returning bestResponse: {} in {} ms", response, watch.elapsed(TimeUnit.MILLISECONDS));
    }
    patchState.response = response;
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private void failTask(PlacementTask patchState, Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, patchState);
  }

  @VisibleForTesting
  protected static PlacementTask buildPatch(TaskState.TaskStage patchStage, Throwable t) {
    PlacementTask state = new PlacementTask();
    state.taskState = new TaskState();
    state.taskState.stage = patchStage;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }

  /**
   * Extracts resource constraints from PlacementTask.
   *
   * @param resource the placement task resources requested
   * @return a list of resource constraints.
   */
  private List<ResourceConstraint> getResourceConstraints(Resource resource)
      throws NoSuchResourceException, SystemErrorException {
    List<ResourceConstraint> constraints = new LinkedList<>();
    if (resource == null) {
      return constraints;
    }

    if (resource.isSetVm() && resource.getVm().isSetResource_constraints()) {
      constraints.addAll(resource.getVm().getResource_constraints());
    }

    if (resource.isSetDisks()) {
      for (Disk disk : resource.getDisks()) {
        if (disk.isSetResource_constraints()) {
          constraints.addAll(disk.getResource_constraints());
        }
      }
    }
    return constraints;
  }
}
