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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskFactoryService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceFactoryService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.ServiceUriPaths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resource allocation task service.
 */
public class ResourceAllocationTaskService extends StatefulService {

  public static final String ID_DELIMITER_CHAR = "-";

  /**
   * SubStage.
   */
  public enum SubStage {
    QUERYING_AVAILABLE_COMPUTE_RESOURCES,
    PROVISIONING_PHYSICAL,
    PROVISIONING_VM_GUESTS,
    PROVISIONING_CONTAINERS,
    FINISHED,
    FAILED
  }

  /**
   * Resource allocation task state.
   */
  public static class ResourceAllocationTaskState extends ServiceDocument {

    /**
     * Mock requests are used for testing. If set, the alloc task will set the isMockRequest
     * bool in the provision task.
     */
    public boolean isMockRequest = false;

    /**
     * Specifies the allowed percentage (between 0 and 1.0) of resources requested to fail
     * before setting the task status to FAILED.
     */
    public double errorThreshold = 0;

    /**
     * Number of compute resources to provision.
     */
    public long resourceCount;

    /**
     * Specifies the resource pool that will be associated with all allocated resources.
     */
    public String resourcePoolLink;

    /**
     * Type of compute to create. Used to find Computes which can create this child.
     */
    public String computeType;

    /**
     * The compute description that defines the resource instances.
     */
    public String computeDescriptionLink;

    /**
     * The disk descriptions used as a templates to create a disk per resource.
     */
    public List<String> diskDescriptionLinks;

    /**
     * The network descriptions used to associate network resources with compute resources.
     */
    public List<String> networkDescriptionLinks;

    /**
     * Custom properties passes in for the resources to be provisioned.
     */
    public Map<String, String> customProperties;

    /**
     * Link to the resource description which overrides the public fields above.
     * This can be used to instantiate the alloc task with a template resource description.
     */
    public String resourceDescriptionLink;

    /**
     * Tracks the task's stages. Set and managed by the run-time.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Tracks the task's substage.
     */
    public SubStage taskSubStage;

    /**
     * List of eligible compute hosts for resource creation requests. Set by the run-time.
     */
    public List<String> parentLinks;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;
  }

  public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES.toMicros(10);

  public static final long QUERY_RETRY_WAIT = 1;

  public static final String CUSTOM_DISPLAY_NAME = "displayName";

  public ResourceAllocationTaskService() {
    super(ResourceAllocationTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      start.fail(new IllegalArgumentException("body is required"));
      return;
    }

    ResourceAllocationTaskState state = getBody(start);

    // if we're passed a resource description link, we'll need to merge state first.
    if (state.resourceDescriptionLink != null) {
      copyResourceDescription(start);
      return;
    }

    validateAndCompleteStart(start, state);
  }

  private void validateAndCompleteStart(Operation start, ResourceAllocationTaskState state) {
    try {
      validateState(state);
    } catch (Exception e) {
      start.fail(e);
      return;
    }

    start.complete();
    startAllocation(state);
  }

  private void startAllocation(ResourceAllocationTaskState state) {
    if (state.taskInfo.stage == TaskStage.CREATED) {
      // start task state machine:
      state.taskInfo.stage = TaskStage.STARTED;
      sendSelfPatch(TaskStage.STARTED, state.taskSubStage, null);
    } else if (state.taskInfo.stage == TaskStage.STARTED) {
      // restart from where the service was interrupted
      logWarning("restarting task...");
      handleStagePatch(state, null);
    } else {
      logFine("Service restarted");
    }
  }

  public void copyResourceDescription(Operation start) {
    ResourceAllocationTaskState state = getBody(start);
    if (state.computeType != null
        || state.computeDescriptionLink != null
        || state.diskDescriptionLinks != null || state.networkDescriptionLinks != null
        || state.customProperties != null) {
      start.fail(new IllegalArgumentException(
          "ResourceDescription overrides ResourceAllocationTaskState"));
      return;
    }

    sendRequest(Operation
        .createGet(this, state.resourceDescriptionLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                sendFailureSelfPatch(e);
                return;
              }
              ResourceDescriptionService.ResourceDescription resourceDesc =
                  o.getBody(ResourceDescriptionService.ResourceDescription.class);

              state.computeType = resourceDesc.computeType;
              state.computeDescriptionLink = resourceDesc.computeDescriptionLink;
              state.diskDescriptionLinks = resourceDesc.diskDescriptionLinks;
              state.networkDescriptionLinks = resourceDesc.networkDescriptionLinks;
              state.customProperties = resourceDesc.customProperties;

              validateAndCompleteStart(start, state);
            }));
  }

  public ResourceAllocationTaskState getBody(Operation op) {
    ResourceAllocationTaskState body = op.getBody(ResourceAllocationTaskState.class);
    return body;
  }

  @Override
  public void handlePatch(Operation patch) {
    ResourceAllocationTaskState body = getBody(patch);
    ResourceAllocationTaskState currentState = getState(patch);

    if (validateTransitionAndUpdateState(patch, body, currentState)) {
      return;
    }

    patch.complete();

    switch (currentState.taskInfo.stage) {
      case CREATED:
        break;
      case STARTED:
        handleStagePatch(currentState, null);
        break;
      case FINISHED:
        logInfo("task is complete");
        break;
      case FAILED:
      case CANCELLED:
        break;
      default:
        break;
    }
  }

  private void handleStagePatch(ResourceAllocationTaskState currentState,
                                ComputeDescriptionService.ComputeDescription desc) {
    if (desc == null) {
      getComputeDescription(currentState);
      return;
    }

    adjustStat(currentState.taskSubStage.toString(), 1);

    switch (currentState.taskSubStage) {
      case QUERYING_AVAILABLE_COMPUTE_RESOURCES:
        doQueryComputeResources(desc, currentState, null);
        break;
      case PROVISIONING_PHYSICAL:
        break;
      case PROVISIONING_VM_GUESTS:
        // intentional fall through
      case PROVISIONING_CONTAINERS:
        doComputeResourceProvisioning(currentState, desc, null);
        break;
      case FAILED:
        break;
      case FINISHED:
        break;
      default:
        break;
    }
  }

  /**
   * Perform a two stage query: Find all compute descriptions that meet our criteria for parent
   * hosts, then find all compute hosts that use any of those descriptions.
   *
   * @param desc
   * @param currentState
   * @param computeDescriptionLinks
   */
  private void doQueryComputeResources(ComputeDescriptionService.ComputeDescription desc,
                                       ResourceAllocationTaskState currentState,
                                       Collection<String> computeDescriptionLinks) {

    if (currentState.computeType == null) {
      throw new IllegalArgumentException("computeType not set");
    }

    String kind = computeDescriptionLinks == null ?
        Utils.buildKind(ComputeDescriptionService.ComputeDescription.class) :
        Utils.buildKind(ComputeService.ComputeState.class);

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(kind);

    QueryTask q = new QueryTask();
    q.querySpec = new QueryTask.QuerySpecification();
    q.querySpec.query.addBooleanClause(kindClause);
    q.tenantLinks = currentState.tenantLinks;

    if (computeDescriptionLinks == null) {
      q.taskInfo.isDirect = true;
      QueryTask.Query hostTypeClause = new QueryTask.Query()
          .setTermPropertyName(
              QueryTask.QuerySpecification
                  .buildCollectionItemName(
                      ComputeDescriptionService.ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
          .setTermMatchValue(currentState.computeType);
      q.querySpec.query.addBooleanClause(hostTypeClause);

      if (desc.zoneId != null && !desc.zoneId.isEmpty()) {
        // we want to make sure the computes we want to alloc are in the same zone as the
        // parent
        QueryTask.Query zoneIdClause = new QueryTask.Query()
            .setTermPropertyName(ComputeDescriptionService.ComputeDescription.FIELD_NAME_ZONE_ID)
            .setTermMatchValue(desc.zoneId);
        q.querySpec.query.addBooleanClause(zoneIdClause);
      }
    } else {
      // we do not want the host query task to be auto deleted too soon and it does
      // not need to complete in-line (the POST can return before the query is done)
      q.taskInfo.isDirect = false;
      q.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;

      // we want compute resources only in the same resource pool as this task
      QueryTask.Query resourcePoolClause = new QueryTask.Query()
          .setTermPropertyName(ComputeService.ComputeState.FIELD_NAME_RESOURCE_POOL_LINK)
          .setTermMatchValue(currentState.resourcePoolLink);
      q.querySpec.query.addBooleanClause(resourcePoolClause);

      // when querying the compute hosts, we want one that has one of the descriptions during
      // the first query
      QueryTask.Query descriptionLinkClause = new QueryTask.Query();

      for (String cdLink : computeDescriptionLinks) {
        QueryTask.Query cdClause = new QueryTask.Query()
            .setTermPropertyName(
                ComputeService.ComputeState.FIELD_NAME_DESCRIPTION_LINK)
            .setTermMatchValue(cdLink);

        cdClause.occurance = Occurance.SHOULD_OCCUR;
        descriptionLinkClause.addBooleanClause(cdClause);
        if (computeDescriptionLinks.size() == 1) {
          // if we only have one compute host description, then all compute hosts must be
          // using it
          descriptionLinkClause = cdClause;
          descriptionLinkClause.occurance = Occurance.MUST_OCCUR;
        }
      }

      q.querySpec.query.addBooleanClause(descriptionLinkClause);
    }

    Operation.CompletionHandler c = (o, ex) -> {
      if (ex != null) {
        sendFailureSelfPatch(ex);
        return;
      }
      this.processQueryResponse(o, currentState, desc, computeDescriptionLinks);
    };

    Operation postQuery = Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(q)
        .setCompletion(c);

    sendRequest(postQuery);
  }

  private void processQueryResponse(Operation op, ResourceAllocationTaskState currentState,
                                    ComputeDescriptionService.ComputeDescription desc,
                                    Collection<String> computeDescriptionLinks) {

    QueryTask rsp = op.getBody(QueryTask.class);
    String queryLink = rsp.documentSelfLink;
    long elapsed = Utils.getNowMicrosUtc() - currentState.documentUpdateTimeMicros;
    if (TaskState.isFailed(rsp.taskInfo)) {
      logWarning("Query failed: %s", Utils.toJsonHtml(rsp.taskInfo.failure));
      sendFailureSelfPatch(new IllegalStateException("Query task failed:"
          + rsp.taskInfo.failure.message));
      return;
    }

    if (!TaskState.isFinished(rsp.taskInfo)) {
      logInfo("Query not complete yet, retrying");
      getHost().schedule(() -> {
        getQueryResults(currentState, desc, queryLink, computeDescriptionLinks);
      }, QUERY_RETRY_WAIT, TimeUnit.SECONDS);
      return;
    }

    if (!rsp.results.documentLinks.isEmpty()) {
      if (computeDescriptionLinks == null) {
        // now do the second stage query
        doQueryComputeResources(desc, currentState, rsp.results.documentLinks);
        return;
      } else {
        SubStage nextStage = determineStageFromHostType(ComputeType
            .valueOf(currentState.computeType));
        currentState.taskSubStage = nextStage;
        currentState.parentLinks = rsp.results.documentLinks;
        sendSelfPatch(currentState);
        return;
      }
    }

    if (elapsed > getHost().getOperationTimeoutMicros()
        && rsp.results.documentLinks.isEmpty()) {
      sendFailureSelfPatch(new IllegalStateException(
          "No compute resources available with poolId:"
              + currentState.resourcePoolLink));
      return;
    }

    logInfo("Reissuing query since no compute hosts were found");
    // retry query. Compute hosts might be created in a different node and might have
    // not replicated yet. We could broadcast the query across nodes (its no more work)
    // but the task should be robust to resources being available after its created
    getHost().schedule(() -> {
      doQueryComputeResources(desc, currentState, computeDescriptionLinks);
    }, QUERY_RETRY_WAIT, TimeUnit.SECONDS);
    return;
  }

  private void getQueryResults(
      ResourceAllocationTaskState currentState,
      ComputeDescriptionService.ComputeDescription desc,
      String queryLink,
      Collection<String> computeDescriptionLinks) {
    sendRequest(Operation.createGet(this, queryLink)
        .setCompletion((o, e) -> {
          if (e != null) {
            logWarning("Failure retrieving query results: %s", e.toString());
            sendFailureSelfPatch(e);
            return;
          }
          processQueryResponse(o, currentState, desc, computeDescriptionLinks);
        }));
  }

  private void doComputeResourceProvisioning(
      ResourceAllocationTaskState currentState,
      ComputeDescriptionService.ComputeDescription desc,
      String subTaskLink) {
    Collection<String> parentLinks = currentState.parentLinks;

    if (subTaskLink == null) {
      // recurse after creating a sub task
      createSubTaskForProvisionCallbacks(currentState, desc);
      return;
    }

    // for human debugging reasons only, prefix the compute host resource id with the allocation
    // task id
    String taskId = getSelfId();

    // we need to create the compute host instances to represent the resources we want to
    // provision and a provision task per resource.
    // Round robin through the parent hosts and assign a parent per child resource
    Iterator<String> parentIterator = null;

    logInfo("Creating %d provision tasks, reporting through sub task %s",
        currentState.resourceCount, subTaskLink);
    String name = "";
    if (currentState.customProperties != null
        && currentState.customProperties.get(CUSTOM_DISPLAY_NAME) != null) {
      name = currentState.customProperties.get(CUSTOM_DISPLAY_NAME);
    }

    for (int i = 0; i < currentState.resourceCount; i++) {
      if (parentIterator == null || !parentIterator.hasNext()) {
        parentIterator = parentLinks.iterator();
      }

      String computeResourceId = taskId + ID_DELIMITER_CHAR + i;

      // We pass null for disk description links and/or network description links if we have
      // any to instantiate. The underlying create calls will do the appropriate GET/POST to
      // create new documents with the same content but unique SELF_LINKs. The last completion
      // to finish will call the create call with the arrays filled in. If there are none, an
      // empty array is passed to jump out of the create calls.
      if (!name.isEmpty()) {
        currentState.customProperties.put(CUSTOM_DISPLAY_NAME, name + i);
      }
      createComputeResource(
          currentState,
          parentIterator.next(),
          computeResourceId,
          currentState.diskDescriptionLinks == null
              || currentState.diskDescriptionLinks.isEmpty() ? new ArrayList<>()
              : null,
          currentState.networkDescriptionLinks == null
              || currentState.networkDescriptionLinks.isEmpty() ? new ArrayList<>()
              : null);

      // as long as you can predict the document self link of a service, you can start
      // services that depend on each other in parallel!
      // The provision task requires a compute host resource, but DCP will automatically
      // register for availability, if the compute host is not created yet. So, we set the
      // link in the provision task, and let the system run in parallel

      String computeResourceLink = UriUtils.buildUriPath(ComputeFactoryService.SELF_LINK,
          computeResourceId);
      ProvisionComputeTaskService.ProvisionComputeTaskState provisionTaskState =
          new ProvisionComputeTaskService.ProvisionComputeTaskState();
      provisionTaskState.computeLink = computeResourceLink;

      // supply the sub task, which keeps count of completions, to the provision tasks.
      // When all provision tasks have PATCHed the sub task to FINISHED, it will issue a
      // single PATCH to us, with stage = FINISHED
      provisionTaskState.parentTaskLink = subTaskLink;
      provisionTaskState.isMockRequest = currentState.isMockRequest;
      provisionTaskState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
      provisionTaskState.tenantLinks = currentState.tenantLinks;
      sendRequest(Operation.createPost(
          UriUtils.buildUri(getHost(), ProvisionComputeTaskFactoryService.SELF_LINK))
          .setBody(provisionTaskState)
          .setCompletion((o, e) -> {
            if (e != null) {
              logSevere("Failure creating provisioning task: %s", Utils.toString(e));
              // we fail on first task failure, we could in theory keep going ...
              sendFailureSelfPatch(e);
              return;
            }
            // nothing to do, the tasks will patch us with finished when each one is
            // done
          }));
    }
  }

  /**
   * we create a sub task that will track the ProvisionComputeHostTask completions.
   */
  private void createSubTaskForProvisionCallbacks(ResourceAllocationTaskState currentState,
                                                  ComputeDescriptionService.ComputeDescription desc) {
    ComputeSubTaskService.ComputeSubTaskState subTaskInitState = new ComputeSubTaskService.ComputeSubTaskState();
    ResourceAllocationTaskState subTaskPatchBody = new ResourceAllocationTaskState();
    subTaskPatchBody.taskSubStage = SubStage.FINISHED;
    subTaskPatchBody.taskInfo.stage = TaskStage.FINISHED;
    // tell the sub task with what to patch us, on completion
    subTaskInitState.parentPatchBody = Utils.toJson(subTaskPatchBody);
    subTaskInitState.errorThreshold = currentState.errorThreshold;

    subTaskInitState.parentTaskLink = getSelfLink();
    subTaskInitState.completionsRemaining = currentState.resourceCount;
    subTaskInitState.tenantLinks = currentState.tenantLinks;
    Operation startPost = Operation
        .createPost(this, UUID.randomUUID().toString())
        .setBody(subTaskInitState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                logWarning("Failure creating sub task: %s", Utils.toString(e));
                sendFailureSelfPatch(e);
                return;
              }
              ComputeSubTaskService.ComputeSubTaskState body = o
                  .getBody(ComputeSubTaskService.ComputeSubTaskState.class);
              // continue, passing the sub task link
              doComputeResourceProvisioning(currentState, desc,
                  body.documentSelfLink);
            });
    getHost().startService(startPost, new ComputeSubTaskService());
  }

  // Create all the dependencies, then create the compute document. createDisk and createNetwork
  // will create their documents, then recurse back here with the appropriate links set.
  private void createComputeResource(ResourceAllocationTaskState currentState,
                                     String parentLink, String computeResourceId, List<String> diskLinks,
                                     List<String> networkLinks) {
    if (diskLinks == null) {
      createDiskResources(currentState, parentLink, computeResourceId, networkLinks);
      return;
    }

    if (networkLinks == null) {
      createNetworkResources(currentState, parentLink, computeResourceId, diskLinks);
      return;
    }

    createComputeHost(currentState, parentLink, computeResourceId, diskLinks, networkLinks);
  }

  private void createComputeHost(ResourceAllocationTaskState currentState, String parentLink,
                                 String computeResourceId,
                                 List<String> diskLinks,
                                 List<String> networkLinks) {
    ComputeService.ComputeState resource = new ComputeService.ComputeState();
    resource.id = computeResourceId;
    resource.parentLink = parentLink;
    resource.descriptionLink = currentState.computeDescriptionLink;
    resource.resourcePoolLink = currentState.resourcePoolLink;
    resource.diskLinks = diskLinks;
    resource.networkLinks = networkLinks;
    resource.customProperties = currentState.customProperties;
    resource.tenantLinks = currentState.tenantLinks;

    sendRequest(Operation.createPost(
        UriUtils.buildUri(getHost(), ComputeFactoryService.SELF_LINK))
        .setBody(resource)
        .setCompletion((o, e) -> {
          if (e != null) {
            logSevere("Failure creating compute resource: %s", Utils.toString(e));
            sendFailureSelfPatch(e);
            return;
          }
          // nothing to do
        }));
  }

  /**
   * Create disks to attach to the compute resource. Use the disk description links to figure out
   * what type of disks to create.
   *
   * @param currentState
   * @param parentLink
   * @param computeResourceId
   */
  private void createDiskResources(ResourceAllocationTaskState currentState,
                                   String parentLink, String computeResourceId, List<String> networkLinks) {

    List<String> diskLinks = new ArrayList<>();
    CompletionHandler diskCreateCompletion = (o, e) -> {
      if (e != null) {
        logWarning("Failure creating disk: %s", e.toString());
        this.sendFailureSelfPatch(e);
        return;
      }

      DiskService.DiskState newDiskState = o.getBody(DiskService.DiskState.class);
      synchronized (diskLinks) {
        diskLinks.add(newDiskState.documentSelfLink);
        if (diskLinks.size() != currentState.diskDescriptionLinks.size()) {
          return;
        }
      }

      // we have created all the disks. Now create the compute host resource
      createComputeResource(currentState, parentLink, computeResourceId, diskLinks,
          networkLinks);
    };

    // get all disk descriptions first, then create new disk using the description/template
    for (String diskLink : currentState.diskDescriptionLinks) {
      sendRequest(Operation.createGet(this, diskLink)
          .setCompletion(
              (o, e) -> {
                if (e != null) {
                  logWarning("Failure getting disk description %s: %s", diskLink,
                      e.toString());
                  this.sendFailureSelfPatch(e);
                  return;
                }

                DiskService.DiskState templateDisk =
                    o.getBody(DiskService.DiskState.class);
                // create a new disk based off the template but use a unique ID
                templateDisk.id = UUID.randomUUID().toString();
                sendRequest(Operation
                    .createPost(this, DiskFactoryService.SELF_LINK)
                    .setBody(templateDisk)
                    .setCompletion(diskCreateCompletion));
              }));
    }
  }

  private void createNetworkResources(ResourceAllocationTaskState currentState,
                                      String parentLink, String computeResourceId, List<String> diskLinks) {
    List<String> networkLinks = new ArrayList<>();
    CompletionHandler networkInterfaceCreateCompletion = (o, e) -> {
      if (e != null) {
        logWarning("Failure creating network interfaces: %s", e.toString());
        this.sendFailureSelfPatch(e);
        return;
      }

      NetworkInterfaceService.NetworkInterfaceState newInterfaceState = o
          .getBody(NetworkInterfaceService.NetworkInterfaceState.class);
      synchronized (networkLinks) {
        networkLinks.add(newInterfaceState.documentSelfLink);
        if (networkLinks.size() != currentState.networkDescriptionLinks.size()) {
          return;
        }
      }

      // we have created all the networks. Now create the compute host resource
      createComputeResource(currentState, parentLink, computeResourceId, diskLinks,
          networkLinks);
    };

    // get all network descriptions first, then create new network interfaces using the
    // description/template
    for (String networkDescLink : currentState.networkDescriptionLinks) {
      sendRequest(Operation
          .createGet(this, networkDescLink)
          .setCompletion(
              (o, e) -> {
                if (e != null) {
                  logWarning("Failure getting network description %s: %s",
                      networkDescLink,
                      e.toString());
                  this.sendFailureSelfPatch(e);
                  return;
                }

                NetworkInterfaceService.NetworkInterfaceState templateNetwork =
                    o.getBody(NetworkInterfaceService.NetworkInterfaceState.class);
                templateNetwork.id = UUID.randomUUID().toString();
                // create a new network based off the template but use a unique ID
                sendRequest(Operation
                    .createPost(this, NetworkInterfaceFactoryService.SELF_LINK)
                    .setBody(templateNetwork)
                    .setCompletion(networkInterfaceCreateCompletion));
              }));
    }
  }

  public String getSelfId() {
    return getSelfLink().substring(getSelfLink()
        .lastIndexOf(UriUtils.URI_PATH_CHAR) + 1);
  }

  private SubStage determineStageFromHostType(ComputeType resourceType) {
    switch (resourceType) {
      case DOCKER_CONTAINER:
        return SubStage.PROVISIONING_CONTAINERS;
      case OS_ON_PHYSICAL:
        return SubStage.PROVISIONING_PHYSICAL;
      case VM_GUEST:
        return SubStage.PROVISIONING_VM_GUESTS;
      case VM_HOST:
        return SubStage.PROVISIONING_PHYSICAL;
      default:
        logSevere("Invalid host type %s, it can not be provisioned", resourceType);
        // this should never happen, due to upstream logic
        return SubStage.FAILED;
    }
  }

  public void getComputeDescription(ResourceAllocationTaskState currentState) {
    sendRequest(Operation
        .createGet(this, currentState.computeDescriptionLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                sendFailureSelfPatch(e);
                return;
              }
              handleStagePatch(currentState,
                  o.getBody(ComputeDescriptionService.ComputeDescription.class));
            }));
  }

  private boolean validateTransitionAndUpdateState(Operation patch,
                                                   ResourceAllocationTaskState body,
                                                   ResourceAllocationTaskState currentState) {

    TaskStage currentStage = currentState.taskInfo.stage;
    SubStage currentSubStage = currentState.taskSubStage;
    boolean isUpdate = false;

    if (body.parentLinks != null) {
      currentState.parentLinks = body.parentLinks;
      isUpdate = true;
    }

    if (body.taskInfo == null || body.taskInfo.stage == null) {
      if (isUpdate) {
        patch.complete();
        return true;
      }
      patch.fail(new IllegalArgumentException("taskInfo and stage are required"));
      return true;
    }

    if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
      patch.fail(new IllegalArgumentException("stage can not move backwards:"
          + body.taskInfo.stage));
      return true;
    }

    if (body.taskInfo.failure != null) {
      logWarning("Referer %s is patching us to failure: %s",
          patch.getReferer(),
          Utils.toJsonHtml(body.taskInfo.failure));
      currentState.taskInfo.failure = body.taskInfo.failure;
      currentState.taskInfo.stage = body.taskInfo.stage;
      currentState.taskSubStage = SubStage.FAILED;
      return false;
    }

    if (currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
      patch.fail(new IllegalArgumentException("subStage can not move backwards:"
          + body.taskSubStage));
      return true;
    }

    currentState.taskInfo.stage = body.taskInfo.stage;
    currentState.taskSubStage = body.taskSubStage;

    logInfo("Moving from %s(%s) to %s(%s)", currentSubStage,
        currentStage,
        body.taskSubStage, body.taskInfo.stage);

    return false;
  }

  private void sendFailureSelfPatch(Throwable e) {
    sendSelfPatch(TaskStage.FAILED, SubStage.FAILED, e);
  }

  private void sendSelfPatch(TaskStage stage, SubStage subStage, Throwable e) {
    ResourceAllocationTaskState body = new ResourceAllocationTaskState();
    body.taskInfo = new TaskState();
    if (e == null) {
      body.taskInfo.stage = stage;
      body.taskSubStage = subStage;
    } else {
      body.taskInfo.stage = TaskStage.FAILED;
      body.taskInfo.failure = Utils.toServiceErrorResponse(e);
      logWarning("Patching to failed: %s", Utils.toString(e));
    }

    sendSelfPatch(body);
  }

  private void sendSelfPatch(ResourceAllocationTaskState body) {
    Operation patch = Operation
        .createPatch(getUri())
        .setBody(body)
        .setCompletion(
            (o, ex) -> {
              if (ex != null) {
                logWarning("Self patch failed: %s", Utils.toString(ex));
              }
            });
    sendRequest(patch);
  }

  public static void validateState(ResourceAllocationTaskState state) {
    if (state.resourcePoolLink == null) {
      throw new IllegalArgumentException(
          "resourcePoolLink is required");
    }

    if (state.taskInfo == null || state.taskInfo.stage == null) {
      state.taskInfo = new TaskState();
      state.taskInfo.stage = TaskStage.CREATED;
    }

    if (state.computeDescriptionLink == null) {
      throw new IllegalArgumentException("computeDescriptionLink is required");
    }

    if (state.resourceCount <= 0) {
      throw new IllegalArgumentException("resourceCount is required");
    }

    if (state.errorThreshold > 1.0 || state.errorThreshold < 0) {
      throw new IllegalArgumentException("errorThreshold can only be between 0 and 1.0");
    }

    if (state.taskSubStage == null) {
      state.taskSubStage = SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
    }

    if (state.documentExpirationTimeMicros == 0) {
      state.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + DEFAULT_TIMEOUT_MICROS;
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
