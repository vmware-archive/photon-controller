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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.UpgradeInformation;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.VibFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VibService;
import com.vmware.photon.controller.deployer.dcp.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChildTaskAggregatorService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTriggerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTriggerTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CopyStateTriggerTaskService.ExecutionState;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.dcp.util.Pair;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements a DCP micro-service which performs the task of
 * initializing migration of an existing deployment to a new deployment.
 */
public class InitializeDeploymentMigrationWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link InitializeDeploymentMigrationWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum SubStage {
      PAUSE_DESTINATION_SYSTEM,
      UPLOAD_VIBS,
      CONTINOUS_MIGRATE_DATA,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link InitializeDeploymentMigrationWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value allows processing of post and patch operations to be
     * disabled, effectively making all service instances listeners. It is set
     * only in test scenarios.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the link to the source management plane in the form of http://address:port.
     */
    @NotNull
    @Immutable
    public String sourceLoadBalancerAddress;

    /**
     * This value represents the id of the destination deployment.
     */
    @NotNull
    @Immutable
    public String destinationDeploymentId;

    /**
     * This value represents the the DeploymentId on source.
     */
    @WriteOnce
    public String sourceDeploymentId;

    /**
     * This value represents the the DeploymentId on source.
     */
    @WriteOnce
    public String sourceZookeeperQuorum;
  }

  public InitializeDeploymentMigrationWorkflowService() {
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

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PAUSE_DESTINATION_SYSTEM;
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
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        if (currentState.sourceZookeeperQuorum == null) {
          populateCurrentState(currentState);
          return;
        }
        processStartedState(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void populateCurrentState(State currentState) throws Throwable {
    final InitializeDeploymentMigrationWorkflowService service = this;
    try {
      getDeployment(currentState, currentState.sourceLoadBalancerAddress,
          new TaskFailingCallback<ResourceList<Deployment>>(service) {

            @Override
            public void onSuccess(ResourceList<Deployment> result) {
              checkState(result != null && result.getItems().size() == 1);
              final String sourceDeploymentId = result.getItems().get(0).getId();
              try {
                getZookeeperQuorumFromSourceSystem(currentState, sourceDeploymentId);
              } catch (Throwable t) {
                failTask(t);
              }
            }
          });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case PAUSE_DESTINATION_SYSTEM:
        pauseDestinationSystem(currentState);
        break;
      case UPLOAD_VIBS:
        migrateHostEntities(currentState);
        break;
      case CONTINOUS_MIGRATE_DATA:
        migrateDataContinously(currentState);
        break;
    }
  }

  private Operation generateKindQuery(Class<?> clazz) {
    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(clazz));
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = typeClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(
                getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS), ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  private void pauseDestinationSystem(final State currentState) {
    ApiClient destinationClient = HostUtils.getApiClient(this);

    FutureCallback<Task> pauseCallback = new TaskFailingCallback<Task>(this) {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_VIBS);
        } catch (Throwable throwable) {
          failTask(throwable);
        }
      }
    };

    try {
      destinationClient.getDeploymentApi().pauseSystemAsync(currentState.destinationDeploymentId, pauseCallback);
    } catch (IOException e) {
      failTask(e);
    }
  }

  private QueryTask.QuerySpecification buildHostQuerySpecification() {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query
        .addBooleanClause(kindClause)
        .addBooleanClause(
            Query.Builder.create()
                .addFieldClause(HostService.State.FIELD_NAME_STATE, HostState.READY.name())
                .build());
    return querySpecification;
  }

  private void migrateHostEntities(State currentState) throws Throwable {
    // run instances of copy state for host migration
    List<UpgradeInformation> hostUpgradeInformation = HostUtils.getDeployerContext(this)
        .getUpgradeInformation().stream()
        .filter(e -> e.destinationFactoryServicePath.equals(HostServiceFactory.SELF_LINK))
        .collect(Collectors.toList());

    Map<String, Pair<Set<InetSocketAddress>, Set<InetSocketAddress>>> m = new HashMap<>();
    ZookeeperClient zookeeperClient
        = ((ZookeeperClientFactoryProvider) getHost()).getZookeeperServerSetFactoryBuilder().create();

    OperationJoin.create(
        hostUpgradeInformation.stream()
            .map(entry -> {
              if (!m.containsKey(entry.zookeeperServerSet)) {
                Set<InetSocketAddress> destinationServers = zookeeperClient.getServers(
                    HostUtils.getDeployerContext(this).getZookeeperQuorum(),
                    entry.zookeeperServerSet);
                Set<InetSocketAddress> sourceServers
                    = zookeeperClient.getServers(currentState.sourceZookeeperQuorum, entry.zookeeperServerSet);

                m.put(entry.zookeeperServerSet, new Pair<>(sourceServers, destinationServers));
              }

              String sourceFactory = entry.sourceFactoryServicePath;
              if (!sourceFactory.endsWith("/")) {
                sourceFactory += "/";
              }
              CopyStateTaskService.State startState
                  = MiscUtils.createCopyStateStartState(
                  m.get(entry.zookeeperServerSet).getFirst(),
                  m.get(entry.zookeeperServerSet).getSecond(),
                  entry.destinationFactoryServicePath, sourceFactory);
              startState.performHostTransformation = Boolean.TRUE;
              return Operation
                  .createPost(this, CopyStateTaskFactoryService.SELF_LINK)
                  .setBody(startState);
            }).collect(Collectors.toList()))
        .setCompletion((es, ts) -> {
          if (ts != null && !ts.isEmpty()) {
            failTask(ts.values());
            return;
          }
          waitUntilCopyStateTasksFinished((operation, throwable) -> {
            if (throwable != null) {
              failTask(throwable);
              return;
            }
            try {
              deleteOldTasks(currentState);
            } catch (Throwable t) {
              failTask(t);
            }
          }, currentState);
        })
        .sendWith(this);
  }

  private void deleteOldTasks(final State currentState) {

    Operation copyStateQuery = generateKindQuery(CopyStateTaskService.State.class);
    Operation uploadVibQuery = generateKindQuery(UploadVibTaskService.State.class);

    OperationJoin.create(copyStateQuery, uploadVibQuery)
        .setCompletion((os, ts) -> {
          if (ts != null && !ts.isEmpty()) {
            failTask(ts.values());
            return;
          }
          Collection<String> linksToDelete = new HashSet<String>();
          for (Operation op : os.values()) {
            linksToDelete.addAll(QueryTaskUtils.getBroadcastQueryDocumentLinks(op));
          }

          if (linksToDelete.isEmpty()) {
            uploadVibs(currentState);
            return;
          }

          OperationJoin.create(
              linksToDelete.stream()
                  .map(link -> {
                    return Operation.createDelete(this, link);
                  })
                  .collect(Collectors.toList())
          )
              .setCompletion((ops, ths) -> {
                if (ths != null && !ths.isEmpty()) {
                  failTask(ths.values());
                  return;
                }
                uploadVibs(currentState);
              })
              .sendWith(this);
        })
        .sendWith(this);
  }

  private void uploadVibs(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(buildHostQuerySpecification()).setDirect(true))
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                uploadVibs(QueryTaskUtils.getBroadcastQueryDocumentLinks(o));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void uploadVibs(Set<String> hostServiceLinks) {

    if (hostServiceLinks.isEmpty()) {
      ServiceUtils.logInfo(this, "Found no hosts to provision");
      sendStageProgressPatch(TaskStage.STARTED, TaskState.SubStage.CONTINOUS_MIGRATE_DATA);
      return;
    }

    File sourceDirectory = new File(HostUtils.getDeployerContext(this).getVibDirectory());
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      throw new IllegalStateException("Invalid VIB source directory " + sourceDirectory);
    }

    File[] vibFiles = sourceDirectory.listFiles((file) -> file.getName().toUpperCase().endsWith(".VIB"));
    if (vibFiles.length == 0) {
      throw new IllegalStateException("No VIB files found in source directory " + sourceDirectory);
    }

    Stream<Operation> vibStartOps = Stream.of(vibFiles).flatMap((vibFile) ->
        hostServiceLinks.stream().map((hostServiceLink) -> {
          VibService.State startState = new VibService.State();
          startState.vibName = vibFile.getName();
          startState.hostServiceLink = hostServiceLink;
          return Operation.createPost(this, VibFactoryService.SELF_LINK).setBody(startState);
        }));

    OperationJoin
        .create(vibStartOps)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                createUploadVibTasks(ops.values());
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void createUploadVibTasks(Collection<Operation> vibStartOps) {

    /**
     * N.B. The error threshold is set to 1.0, which means that the aggregator service will not
     * report failure even if all of the child tasks fail. Failures in VIB upload tasks will be
     * reflected in host provisioning failures during finalize.
     */

    ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
    startState.parentTaskLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(buildPatch(TaskStage.STARTED, TaskState.SubStage.CONTINOUS_MIGRATE_DATA,
        null));
    startState.pendingCompletionCount = vibStartOps.size();
    startState.errorThreshold = 1.0;

    sendRequest(Operation
        .createPost(this, ChildTaskAggregatorFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                createUploadVibTasks(vibStartOps, o.getBody(ServiceDocument.class).documentSelfLink);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createUploadVibTasks(Collection<Operation> vibStartOps, String aggregatorServiceLink) {

    Stream<Operation> taskStartOps = vibStartOps.stream().map((vibStartOp) -> {
      UploadVibTaskService.State startState = new UploadVibTaskService.State();
      startState.parentTaskServiceLink = aggregatorServiceLink;
      startState.vibServiceLink = vibStartOp.getBody(ServiceDocument.class).documentSelfLink;
      return Operation.createPost(this, UploadVibTaskFactoryService.SELF_LINK).setBody(startState);
    });

    OperationJoin
        .create(taskStartOps)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
              }
            })
        .sendWith(this);
  }

  private void migrateDataContinously(State currentState) {
    // Start MigrationStatusUpdateService
    MigrationStatusUpdateTriggerService.State startState = new MigrationStatusUpdateTriggerService.State();
    startState.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + currentState.destinationDeploymentId;
    startState.documentSelfLink = currentState.destinationDeploymentId;

    OperationSequence
        .create(createStartMigrationOperations(currentState))
        .setCompletion((os, ts) -> {
          if (ts != null) {
            failTask(ts.values());
          }
        })
        .next(Operation
            .createPost(UriUtils.buildUri(getHost(), MigrationStatusUpdateTriggerFactoryService.SELF_LINK, null))
            .setBody(startState))
        .setCompletion((os, ts) -> {
          if (ts != null) {
            failTask(ts.values());
            return;
          }
          sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        })
        .sendWith(this);
  }

  private OperationJoin createStartMigrationOperations(State currentState) {
    Map<String, Pair<Set<InetSocketAddress>, Set<InetSocketAddress>>> m = new HashMap<>();
    ZookeeperClient zookeeperClient
        = ((ZookeeperClientFactoryProvider) getHost()).getZookeeperServerSetFactoryBuilder().create();

    return OperationJoin.create(
        HostUtils.getDeployerContext(this).getUpgradeInformation().stream()
            .map(entry -> {
              if (!m.containsKey(entry.zookeeperServerSet)) {
                Set<InetSocketAddress> destinationServers = zookeeperClient.getServers(
                    HostUtils.getDeployerContext(this).getZookeeperQuorum(),
                    entry.zookeeperServerSet);
                Set<InetSocketAddress> sourceServers
                    = zookeeperClient.getServers(currentState.sourceZookeeperQuorum, entry.zookeeperServerSet);

                m.put(entry.zookeeperServerSet, new Pair<>(sourceServers, destinationServers));
              }

              String destinationFactoryLink = entry.destinationFactoryServicePath;
              String sourceFactoryLink = entry.sourceFactoryServicePath;

              InetSocketAddress remote = ServiceUtils.selectRandomItem(m.get(entry.zookeeperServerSet).getSecond());
              CopyStateTriggerTaskService.State startState = new CopyStateTriggerTaskService.State();
              startState.sourceServers = new HashSet<>();
              for (InetSocketAddress sourceServer : m.get(entry.zookeeperServerSet).getFirst()) {
                startState.sourceServers.add(new Pair<>(sourceServer.getHostName(), sourceServer.getPort()));
              }
              startState.destinationIp = remote.getAddress().getHostAddress();
              startState.destinationPort = remote.getPort();
              startState.factoryLink = destinationFactoryLink;
              startState.sourceFactoryLink = sourceFactoryLink;
              startState.documentSelfLink = UUID.randomUUID().toString() + startState.factoryLink;
              startState.executionState = ExecutionState.RUNNING;
              startState.performHostTransformation = Boolean.TRUE;
              return Operation
                  .createPost(this, CopyStateTriggerTaskFactoryService.SELF_LINK)
                  .setBody(startState);
            }).collect(Collectors.toList()));
  }

  private void waitUntilCopyStateTasksFinished(CompletionHandler handler, State currentState) {
    // wait until all the copy-state services are done
    generateQueryCopyStateTaskQuery()
        .setCompletion((op, t) -> {
          if (t != null) {
            handler.handle(op, t);
            return;
          }
          List<CopyStateTaskService.State> documents =
              QueryTaskUtils.getBroadcastQueryDocuments(CopyStateTaskService.State.class, op);
          List<CopyStateTaskService.State> runningServices = documents.stream()
              .filter((d) -> d.taskState.stage == TaskStage.CREATED || d.taskState.stage == TaskStage.STARTED)
              .collect(Collectors.toList());
          if (runningServices.isEmpty()) {
            handler.handle(op, t);
            return;
          }
          getHost().schedule(
              () -> waitUntilCopyStateTasksFinished(handler, currentState),
              currentState.taskPollDelay,
              TimeUnit.MILLISECONDS);
        })
        .sendWith(this);
  }

  private Operation generateQueryCopyStateTaskQuery() {
    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(CopyStateTaskService.State.class)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build();
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask);
  }

  private void getZookeeperQuorumFromSourceSystem(
      State currentState,
      String sourceDeploymentId)
      throws Throwable {
    MiscUtils.getZookeeperQuorumFromSourceSystem(this, currentState.sourceLoadBalancerAddress,
        sourceDeploymentId, currentState.taskPollDelay, new TaskFailingCallback<List<String>>(this) {
          @Override
          public void onSuccess(@Nullable List<String> ipAddresses) {
            String zookeeperQuorum = MiscUtils.generateReplicaList(ipAddresses, Integer.toString(ServicePortConstants
                .ZOOKEEPER_PORT));

            ServiceUtils.logInfo(InitializeDeploymentMigrationWorkflowService.this,
                "Set Zookeeper quorum %s", zookeeperQuorum);
            State patchState = buildPatch(currentState.taskState.stage, currentState.taskState.subStage, null);
            patchState.sourceZookeeperQuorum = zookeeperQuorum;
            patchState.sourceDeploymentId = sourceDeploymentId;
            TaskUtils.sendSelfPatch(InitializeDeploymentMigrationWorkflowService.this, patchState);
          }
        });
  }

  private void getDeployment(final State currentState,
                             String endpoint,
                             FutureCallback<ResourceList<Deployment>> callback)
      throws IOException {
    ApiClient client = null;
    if (endpoint != null) {
      client = HostUtils.getApiClient(this, endpoint);
    } else {
      client = HostUtils.getApiClient(this);
    }
    client.getDeploymentApi().listAllAsync(callback);
  }

  private State applyPatch(State currentState, State patchState) {
    if (patchState.taskState.stage != currentState.taskState.stage
        || patchState.taskState.subStage != currentState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      currentState.taskState = patchState.taskState;
    }

    if (null != patchState.sourceZookeeperQuorum) {
      currentState.sourceZookeeperQuorum = patchState.sourceZookeeperQuorum;
    }

    if (null != patchState.sourceDeploymentId) {
      currentState.sourceDeploymentId = patchState.sourceDeploymentId;
    }

    return currentState;
  }


  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case PAUSE_DESTINATION_SYSTEM:
          break;
        case CONTINOUS_MIGRATE_DATA:
        case UPLOAD_VIBS:
          checkState(null != currentState.sourceDeploymentId);
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage);
      }
    }
  }

  private void validateTaskSubStage(TaskState taskState) {
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (null != currentState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  private void failTask(Collection<Throwable> failures) {
    failures.forEach((throwable) -> ServiceUtils.logSevere(this, throwable));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
  }

  @VisibleForTesting
  protected static State buildPatch(
      TaskState.TaskStage patchStage,
      @Nullable TaskState.SubStage patchSubStage,
      @Nullable Throwable t) {

    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }

  private abstract static class TaskFailingCallback<T> implements FutureCallback<T> {
    private InitializeDeploymentMigrationWorkflowService service;

    public TaskFailingCallback(InitializeDeploymentMigrationWorkflowService service) {
      this.service = service;
    }

    @Override
    public void onFailure(Throwable t) {
      service.failTask(t);
    }
  }
}
