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

package com.vmware.photon.controller.rootscheduler.xenon.task;

import com.vmware.photon.controller.cloudstore.dcp.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.Vm;
import com.vmware.photon.controller.rootscheduler.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ScoreCalculator;
import com.vmware.photon.controller.rootscheduler.xenon.ConstraintCheckerProvider;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.photon.controller.rootscheduler.xenon.ScoreCalculatorProvider;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The main responsibility of this class it to pick hosts for VM/disk placements. The
 * placement algorithm is roughly based on Sparrow scheduler (1), and it works as follows:
 * <p>
 * 1. Randomly choose n hosts (n = 4 by default) that satisfy all the resource constraints.
 * 2. Send place requests to the chosen hosts and wait for responses with a timeout.
 * 3. After receiving all the responses or reaching the timeout, return the host with
 * the highest placement score. See {@link ScoreCalculator} for the placement score
 * calculation logic.
 * <p>
 * (1) http://www.eecs.berkeley.edu/~keo/publications/sosp13-final17.pdf
 */
public class PlacementTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.SCHEDULER_ROOT + "/placement";

  // This completion handler provides a new constraint to be added to a set of constraints
  interface CalculateConstraintCompletion {
    public void handle(ResourceConstraint newConstraint, Exception exception);
  }

  // This completion handler returns a set of candidate hosts
  // It will probably move into the constraint checker interface later
  interface GetCandidatesCompletion {
    public void handle(Map<String, ServerAddress> candidates, Exception exception);
  }

  /**
   * This class implements a Xenon micro-service that provides a factory for
   * {@link PlacementTaskService} instances.
   */
  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(PlacementTaskService.class);
  }

  public PlacementTaskService() {
    super(PlacementTask.class);
    // The placement task serves select a host for VM and disk placements, each task will handle a single
    // request so there is no need for multiple nodes to have the same information nor a specific node to be
    // the leader of this operation. Persistence is not needed since on a failure, the operation will retry with
    // more up to date host information for the placements.
    super.toggleOption(ServiceOption.PERSISTENCE, false);
    super.toggleOption(ServiceOption.REPLICATION, false);
    super.toggleOption(ServiceOption.OWNER_SELECTION, false);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    PlacementTask startState = start.getBody(PlacementTask.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        start.setBody(startState).complete();
      } else if (startState.taskState.isDirect) {
        handlePlaceRequest(startState, start);
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        start.setBody(startState).complete();
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.isDirect, null));
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, startState.taskState.isDirect, t), t, start);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    PlacementTask currentState = getState(patchOperation);
    PlacementTask patchState = patchOperation.getBody(PlacementTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        handlePlaceRequest(currentState, null);
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, false, t), t, null);
    }
  }

  private void validateState(PlacementTask startState) {
    ValidationUtils.validateState(startState);
    if (!startState.taskState.isDirect) {
      ValidationUtils.validateTaskStage(startState.taskState);
    }
  }

  private void validatePatchState(PlacementTask currentState, PlacementTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  /**
   * This method gets valid host candidates based on requested resource constraints,
   * sends a place request to each of the hosts, and selects the best candidate from
   * the responses from the candidates.
   *
   * @param currentState
   */
  private void handlePlaceRequest(PlacementTask currentState, Operation postOperation) {
    initRequestId(currentState);
    Stopwatch placementWatch = Stopwatch.createStarted();

    // Note that getPotentialCandidates is asynchronous, so we handle the response via a completion
    getPotentialCandidates(currentState,
        (candidates, ex) -> {
          if (ex != null) {
            handleGetCandidateError(currentState, postOperation, ex);
            return;
          }
          handleGetCandidateResult(currentState, postOperation, candidates, placementWatch);
        });
  }

  /**
   * Helper method to handle errors from getPotentialCandidates().
   */
  private void handleGetCandidateError(PlacementTask currentState, Operation postOperation, Exception ex) {
    PlacementTask patchState = buildPatch(TaskState.TaskStage.FAILED, currentState.taskState.isDirect, ex);
    patchState.error = ex.getMessage();
    patchState.requestId = currentState.requestId;
    if (ex instanceof NoSuchResourceException) {
      patchState.resultCode = PlaceResultCode.NO_SUCH_RESOURCE;
    } else {
      patchState.resultCode = PlaceResultCode.SYSTEM_ERROR;
    }
    failTask(patchState, ex, postOperation);
  }

  /**
   * Helper method to process the candidates returned by getPotentialCandidates.
   */
  private void handleGetCandidateResult(
      PlacementTask currentState,
      Operation postOperation,
      Map<String, ServerAddress> candidates,
      Stopwatch placementWatch) {

    ServiceUtils.logInfo(this, "elapsed-time flat-place-get-candidates %d milliseconds",
        placementWatch.elapsed(TimeUnit.MILLISECONDS));

    if (candidates.isEmpty()) {
      String msg = String.format("Place failure, constraints cannot be satisfied for request: %s",
          currentState.resource);
      PlacementTask patchState = buildPatch(TaskState.TaskStage.FAILED, currentState.taskState.isDirect, null);
      patchState.resultCode = PlaceResultCode.NO_SUCH_RESOURCE;
      patchState.error = msg;
      patchState.requestId = currentState.requestId;
      failTask(patchState, new Throwable(msg), postOperation);
      return;
    }

    // Send place request to the candidates to get a score for each one
    ServiceUtils.logInfo(this, "Sending place requests to %s with timeout %d ms", candidates, currentState.timeoutMs);
    Stopwatch scoreCandidatesStopwatch = Stopwatch.createStarted();
    final Set<PlaceResponse> okResponses = Sets.newConcurrentHashSet();
    final Set<PlaceResultCode> returnCodes = Sets.newConcurrentHashSet();

    sendPlaceRequests(currentState, candidates, okResponses, returnCodes);

    ServiceUtils.logInfo(this, "elapsed-time flat-place-score-candidates %d milliseconds",
        scoreCandidatesStopwatch.elapsed(TimeUnit.MILLISECONDS));

    // Return the best response.
    PlacementTask patchState = selectBestResponse(okResponses, returnCodes, currentState, placementWatch);
    if (postOperation == null) {
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      postOperation.setBody(patchState).complete();
    }
  }

  /**
   * Retrieves potential hosts from constraint checker that satisfy the resource constraints from the current state.
   *
   * @param currentState
   * @return
   * @throws NoSuchResourceException
   * @throws SystemErrorException
   */
  private void getPotentialCandidates(PlacementTask currentState, GetCandidatesCompletion completion) {
    // Get the list of resource constraints
    List<ResourceConstraint> constraints;

    constraints = getResourceConstraints(currentState.resource);

    // Note: createImageSeedingConstraint is asynchronous (it queries Cloudstore), so we use a completion
    createImageSeedingConstraint(currentState.resource,
        (newConstraint, ex) -> {
          if (ex != null) {
            // createImageSeedingConstraint handled logging, we just pass on the exception
            completion.handle(null, ex);
            return;
          }
          if (newConstraint != null) {
            constraints.add(newConstraint);
          }
          logConstraints(constraints);

          applyConstraintChecker(currentState, constraints, completion);
        });
    return;
  }

  /**
   * Used by getPotentialCandidates to query the constraint checker and return the results in the completion.
   */
  private void applyConstraintChecker(
      PlacementTask currentState,
      List<ResourceConstraint> constraints,
      GetCandidatesCompletion completion) {

    ConstraintChecker checker = ((ConstraintCheckerProvider) getHost()).getConstraintChecker();
    Map<String, ServerAddress> candidates = null;
    try {
      candidates = checker.getCandidates(constraints, currentState.sampleHostCount);
      completion.handle(candidates, null);
    } catch (Exception ex) {
      completion.handle(null, ex);
    }
    return;
  }

  /**
   * Sends the placement requests to the potential hosts and adds successful responses into okResponses
   * and all the response of the hosts into returnCodes.
   *
   * @param currentState
   * @param candidates   the hosts to send place requests
   * @param okResponses  set of the successful responses
   * @param returnCodes  set of the responses
   */
  private void sendPlaceRequests(PlacementTask currentState, Map<String, ServerAddress> candidates,
                                 Set<PlaceResponse> okResponses, Set<PlaceResultCode> returnCodes) {
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
            ServiceUtils.logInfo(PlacementTaskService.this, "Received a place response from %s: %s", entry, response);
            returnCodes.add(response.getResult());
            if (response.getResult() == PlaceResultCode.OK) {
              okResponses.add(response);
            }
            done.countDown();
          }

          @Override
          public void onError(Exception ex) {
            ServiceUtils.logWarning(PlacementTaskService.this, "Failed to get a placement response from %s: %s",
                entry, ex);
            done.countDown();
          }
        });
      } catch (RpcException ex) {
        ServiceUtils.logWarning(this, "Failed to get a placement response from %s: %s", entry, ex);
        done.countDown();
      }
    }

    // Wait for responses to come back.
    try {
      done.await(currentState.timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      ServiceUtils.logTrace(this, "Got interrupted waiting for place responses: %s", ex);
    }
  }

  /**
   * Returns the best host selected host among successful responses. If there are not any hosts to place the request,
   * this returns a result from the host responses.
   *
   * @param okResponses
   * @param returnCodes
   * @param watch
   * @return
   */
  private PlacementTask selectBestResponse(Set<PlaceResponse> okResponses, Set<PlaceResultCode> returnCodes,
                                           PlacementTask currentState, Stopwatch watch) {
    ScoreCalculator scoreCalculator = ((ScoreCalculatorProvider) getHost()).getScoreCalculator();
    PlaceResponse response = scoreCalculator.pickBestResponse(okResponses);
    watch.stop();

    PlacementTask patchState;
    if (response == null) {
      patchState = buildPatch(TaskState.TaskStage.FAILED, currentState.taskState.isDirect, null);
      PlaceResultCode errorCode;
      String errorMsg;
      if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)) {
        errorCode = PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE;
        errorMsg = "Not enough cpu resources available";
      } else if (returnCodes.contains(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)) {
        errorCode = PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE;
        errorMsg = "Not enough memory resources available";
      } else if (returnCodes.contains((PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY))) {
        errorCode = PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY;
        errorMsg = "Not enough capacity in the datastore available";
      } else if (returnCodes.contains(PlaceResultCode.NO_SUCH_RESOURCE)) {
        errorCode = PlaceResultCode.NO_SUCH_RESOURCE;
        errorMsg = "No such resource";
      } else if (returnCodes.contains(PlaceResultCode.INVALID_SCHEDULER)) {
        errorCode = PlaceResultCode.INVALID_SCHEDULER;
        errorMsg = "Invalid scheduler";
      } else {
        errorCode = PlaceResultCode.SYSTEM_ERROR;
        errorMsg = String.format("Received no response in %d ms", watch.elapsed(TimeUnit.MILLISECONDS));
      }
      ServiceUtils.logSevere(this, errorMsg);
      patchState.resultCode = errorCode;
      patchState.error = errorMsg;
      patchState.requestId = currentState.requestId;
    } else {
      patchState = buildPatch(TaskState.TaskStage.FINISHED, currentState.taskState.isDirect, null);
      ServiceUtils.logInfo(this, "Returning bestResponse: %s in %d ms", response, watch.elapsed(TimeUnit.MILLISECONDS));
      patchState.resultCode = response.getResult();
      patchState.generation = response.getGeneration();
      patchState.serverAddress = response.getAddress();
      patchState.resource = new Resource();
      patchState.resource.setPlacement_list(response.getPlacementList());
      patchState.requestId = currentState.requestId;
    }
    return patchState;
  }

  /**
   * This reports the error that caused the failure state of patchState before sending an update
   * to itself.
   * @param patchState the failed PlacementTask
   * @param t the error associated with the failed PlacementTask
   * @param postOperation if there is a postOperation, this is part of a direct task and will return
   *                      once this update is complete, otherwise moves to a failed state
   */
  private void failTask(PlacementTask patchState, Throwable t, Operation postOperation) {
    ServiceUtils.logSevere(this, t);
    if (postOperation == null) {
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      postOperation.setBody(patchState).complete();
    }
  }

  /**
   * Sets a unique request id for the PlacementTask if it has not been set. This logs the
   * request id as it completes its operation.
   * @param currentState the PlacementTask
   */
  private static void initRequestId(PlacementTask currentState) {
    if (currentState.requestId == null) {
      currentState.requestId = UUID.randomUUID().toString();
    }
    LoggingUtils.setRequestId(currentState.requestId);
  }

  /**
   * Builds a new PlacementTask with the specified stage and isDirect boolean.
   * If Throwable t is set then the failure response is added to the task state.
   * @param patchStage the stage to set the created PlacementTask
   * @param isDirect boolean if the PlacementTask is a direct operation.
   * @param t the error associated with this PlacementTask, if one occurred.
   * @return
   */
  @VisibleForTesting
  protected static PlacementTask buildPatch(TaskState.TaskStage patchStage, boolean isDirect,
                                            Throwable t) {
    PlacementTask state = new PlacementTask();
    state.taskState = new TaskState();
    state.taskState.stage = patchStage;
    state.taskState.isDirect = isDirect;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }

  /**
   * Extracts resource constraints from the resource.
   *
   * @param resource
   *          the placement task resources requested
   * @return a list of resource constraints.
   */
  private List<ResourceConstraint> getResourceConstraints(Resource resource) {
    List<ResourceConstraint> constraints = new LinkedList<>();
    if (resource == null) {
      return constraints;
    }

    if (resource.isSetVm()) {
      Vm vm = resource.getVm();
      if (vm.isSetResource_constraints()) {
        constraints.addAll(vm.getResource_constraints());
      }
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

  /**
   * New images may not be available on all the image datastores because the replication is in progress.
   * We look at image seeding information available in cloud-store to add placement constraints
   * such that only hosts with the requested image on an attached image datastore are selected in the
   * placement process.
   *
   * Note that this does not directly return a result, but it provides it via a completion. This is because
   * the implementation requires making a call to Cloudstore, and we do that asynchronously.
   */
  public void createImageSeedingConstraint(Resource resource, CalculateConstraintCompletion completion) {

    if (resource == null || !resource.isSetVm() || resource.getVm() == null) {
      // Nothing to add, complete immediately
      completion.handle(null, null);
      return;
    }

    // It is necessary for a VM placement request to have an associated diskImage. If none are
    // found, we fail placement.
    String imageId = extractImageIdFromResource(resource);
    if (imageId == null) {
      String errorMsg = "VM resource does not have an associated diskImage";
      ServiceUtils.logSevere(this, errorMsg);
      completion.handle(null, new SystemErrorException(errorMsg));
      return;
    }

    try {
      // Query Cloudstore for all Image to Image Datastore mappings with the given imageId.
      // Note that we don't need to do a broadcast query, because these mappings use symmetric
      // replication and so are on all hosts.
      final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      termsBuilder.put("imageId", imageId);

      CloudStoreHelper cloudStoreHelper = ((SchedulerXenonHost) getHost()).getCloudStoreHelper();
      URI queryUri = cloudStoreHelper.selectLocalCloudStoreIfAvailable(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildQuerySpec(ImageToImageDatastoreMappingService.State.class, termsBuilder.build());
      spec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
      QueryTask queryTask = QueryTask.create(spec).setDirect(true);
      Operation postQuery = Operation.createPost(queryUri)
          .setBody(queryTask)
          .setReferer(this.getHost().getPublicUri())
          .setContextId(LoggingUtils.getRequestId())
          .setCompletion((op, ex) -> {
            try {
              handleImageDatastoreResponse(op, ex, imageId, completion);
            } catch (Throwable t) {
              String error = "Internal error in image datastore query response handling.";
              ServiceUtils.logSevere(this, error, t);
              completion.handle(null, new SystemErrorException(error));
              return;
            }
          });

      this.sendRequest(postQuery);
    } catch (Throwable t) {
      String error = "Internal error in image datastore query.";
      ServiceUtils.logSevere(this, error, t);
      completion.handle(null, new SystemErrorException(error));
      return;
    }
  }

  /**
   * Given a resource from a placement request, extract the imageID for the associated VM.
   * If there is no imageId, return null.
   */
  private String extractImageIdFromResource(Resource resource) {
    String imageId = null;

    Vm vm = resource.getVm();
    if (vm != null && vm.isSetDisks()) {
      for (Disk disk : vm.getDisks()) {
        if (disk.isSetImage()) {
          imageId = disk.getImage().getId();
          break;
        }
      }
    }
    return imageId;
  }

  /**
   * Helper for createImageSeedingConstraint.
   * Handles response from query task service: extracts all image datastores that
   * contain the imageId we need, and creates a ResourceConstraint for them.
   */
  private void handleImageDatastoreResponse(
      Operation response,
      Throwable ex,
      String imageId,
      CalculateConstraintCompletion completion) {

    // First, check for failure
    if (ex != null) {
      String error = String.format("Failed to call cloud-store to lookup image datastores for image %d");
      ServiceUtils.logSevere(this, error, ex);
      Exception cloudStoreEx = new SystemErrorException(error + ": " + ex.getMessage());
      completion.handle(null, cloudStoreEx);
      return;
    }

    // Extract the image datastore IDs
    List<String> seededImageDatastores = new ArrayList<>();
    ServiceDocumentQueryResult queryResult = response.getBody(QueryTask.class).results;
    queryResult.documents.values().forEach(item -> {
      String datastoreId = Utils.fromJson(
          item, ImageToImageDatastoreMappingService.State.class).imageDatastoreId;
      seededImageDatastores.add(datastoreId);
    });

    // Fail if we have no image datastore IDs: the VM can't be placed unless at least one exists
    if (seededImageDatastores.isEmpty()) {
      String error = "No seeded image datastores found for the imageId: " + imageId;
      ServiceUtils.logWarning(this, error);
      completion.handle(null, new NoSuchResourceException(error));
      return;
    }

    // Construct the new Resource Constraint
    ResourceConstraint constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.DATASTORE);
    constraint.setValues(seededImageDatastores);
    completion.handle(constraint, null);
  }

  /**
   * Log the constraints. Because the ResourceConstraint class was auto-generated by thrift,
   * it doesn't print particularly nicely, so do that here.
   *
   * We print a JSON string. It's all on one line (to avoid interleaving in the log file)
   * so it's not hugely readable. But it's easy to reformat JSON to be readable.
   *
   * When we move this away from thrift, we can just convert the objects directly to JSON.
   */
  private void logConstraints(List<ResourceConstraint> constraints) {
    StringBuilder output = new StringBuilder();
    output.append("Resource constraints: [ ");
    if (constraints != null) {
      for (ResourceConstraint constraint : constraints) {
        if (constraint != null) {
          output.append("{ ");

          // Part 1: Is it a negative constraint?
          output.append("\"negative\": ");
          if (constraint.isSetNegative()) {
            output.append(String.valueOf(constraint.isNegative()));
          } else {
            output.append("false");
          }

          // Part 2: Type
          output.append(", \"type\": ");
          if (!constraint.isSetType()) {
            output.append("\"unknown\"");
          } else {
            switch(constraint.getType()) {
              case DATASTORE:
                output.append("\"datastore\"");
                break;
              case HOST:
                output.append("\"host\"");
                break;
              case NETWORK:
                output.append("\"network\"");
                break;
              case AVAILABILITY_ZONE:
                output.append("\"availability-zone\"");
                break;
              case DATASTORE_TAG:
                output.append("\"datastore-tag\"");
                break;
              case MANAGEMENT_ONLY:
                output.append("\"management-only\"");
                break;
              default:
                output.append("\"unknown\"");
                break;
            }
          }

          // Part 3: Values
          if (constraint.isSetValues()) {
            List<String> values = constraint.getValues();
            output.append(", \"values\": [ ");
            boolean first = true;
            for (String value : values) {
              if (!first) {
                output.append(", ");
              }
              first = false;
              output.append('\"');
              output.append(value);
              output.append('\"');
            }
            output.append(" ]");
          }

          output.append(" }");
        }
      }
    }
    output.append(" ]");
    ServiceUtils.logInfo(this, "%s", output.toString());
  }
}
