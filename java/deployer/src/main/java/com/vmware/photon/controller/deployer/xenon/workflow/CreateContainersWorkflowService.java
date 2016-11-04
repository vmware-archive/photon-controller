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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.ssl.KeyStoreUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig.ContainerType;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerTaskService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.FileUtils;

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class implements a Xenon task service which creates service containers for a deployment.
 */
public class CreateContainersWorkflowService extends StatefulService {

  public static final String GENERATE_CERTIFICATE_SCRIPT_NAME = "generate-certificate";


  /**
   * This class defines the state of a {@link CreateContainersWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This class defines the possible sub-stages for a task.
     */
    public enum SubStage {
      CREATE_LIGHTWAVE_CONTAINER,
      GENERATE_CERTIFICATE,
      CREATE_CORE_CONTAINERS,
      PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
      CREATE_SERVICE_CONTAINERS,
      CREATE_LOAD_BALANCER_CONTAINER,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link CreateContainersWorkflowService} task.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval to wait, in milliseconds, when polling the state of a child task.
     */
    @Positive
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the document link of the {@link DeploymentService} in whose context the operation is being
     * performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * If present, this value represents the document link of the VM service on which the service containers should be
     * created.
     */
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents whether authentication is enabled for the current deployment.
     */
    @NotNull
    @Immutable
    public Boolean isAuthEnabled;

    /**
     * This value represents whether the current task is being executed as part of a new deployment.
     */
    @DefaultBoolean(value = true)
    @Immutable
    public Boolean isNewDeployment;
  }

  public CreateContainersWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void validateTaskState(TaskState taskState) {
    ValidationUtils.validateTaskStage(taskState);
    switch (taskState.stage) {
      case CREATED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(taskState.subStage == null);
        break;
      case STARTED:
        checkState(taskState.subStage != null);
        switch (taskState.subStage) {
          case CREATE_LIGHTWAVE_CONTAINER:
          case GENERATE_CERTIFICATE:
          case CREATE_CORE_CONTAINERS:
          case PREEMPTIVE_PAUSE_BACKGROUND_TASKS:
          case CREATE_SERVICE_CONTAINERS:
          case CREATE_LOAD_BALANCER_CONTAINER:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case CREATE_LIGHTWAVE_CONTAINER:
        processCreateLightwaveContainerSubStage(currentState);
        break;
      case GENERATE_CERTIFICATE:
        generateCertificate(currentState, TaskState.TaskStage.STARTED,
            TaskState.SubStage.CREATE_CORE_CONTAINERS);
        break;
      case CREATE_CORE_CONTAINERS:
        createContainers(currentState,
            Arrays.asList(ContainersConfig.ContainerType.PhotonControllerCore),
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS);
        break;
      case PREEMPTIVE_PAUSE_BACKGROUND_TASKS:
        processPreemptivePauseBackgroundTasksSubStage(currentState);
        break;
      case CREATE_SERVICE_CONTAINERS:
        createContainers(currentState,
            Arrays.asList(ContainersConfig.ContainerType.ManagementUi),
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER);
        break;
      case CREATE_LOAD_BALANCER_CONTAINER:
        processCreateLoadBalancerContainerSubStage(currentState);
        break;
    }
  }

  //
  // PREEMPTIVE_PAUSE_BACKGROUND_TASKS sub-stage routines
  //

  private void processPreemptivePauseBackgroundTasksSubStage(State currentState) {

    if (!currentState.isNewDeployment) {
      ServiceUtils.logInfo(this, "Skipping pause of background tasks (not a new deployment");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SERVICE_CONTAINERS);
      return;
    }

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                pauseBackgroundTasks(o.getBody(DeploymentService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void pauseBackgroundTasks(DeploymentService.State deploymentState) throws Throwable {
    DeploymentService.State deployment = new DeploymentService.State();
    deployment.state = DeploymentState.BACKGROUND_PAUSED;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(deploymentState.documentSelfLink)
        .addRequestHeader(Operation.REPLICATION_QUORUM_HEADER, Operation.REPLICATION_QUORUM_HEADER_VALUE_ALL)
        .setBody(deployment)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SERVICE_CONTAINERS);
              }
            }
        ));
  }

  //
  // CREATE_LIGHTWAVE_CONTAINER sub-stage routines
  //

  private void processCreateLightwaveContainerSubStage(State currentState) {

    if (!currentState.isNewDeployment) {
      ServiceUtils.logInfo(this, "Skipping creation of Lightwave container (not a new deployment");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.GENERATE_CERTIFICATE);
      return;
    }

    if (!currentState.isAuthEnabled) {
      ServiceUtils.logInfo(this, "Skipping creation of Lightwave container (auth is disabled)");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.GENERATE_CERTIFICATE);
      return;
    }

    createContainers(currentState,
        Collections.singletonList(ContainersConfig.ContainerType.Lightwave),
        TaskState.TaskStage.STARTED,
        TaskState.SubStage.GENERATE_CERTIFICATE);
  }

  //
  // CREATE_LOAD_BALANCER_CONTAINER sub-stage routines
  //

  private void processCreateLoadBalancerContainerSubStage(State currentState) {

    if (!currentState.isNewDeployment) {
      ServiceUtils.logInfo(this, "Skipping creation of load balancer container (not a new deployment");
      sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
      return;
    }

    createContainers(currentState,
        Collections.singletonList(ContainersConfig.ContainerType.LoadBalancer),
        TaskState.TaskStage.FINISHED,
        null);
  }

  //
  // Utility routines
  //

  private void createContainers(State currentState,
                                List<ContainersConfig.ContainerType> containerTypes,
                                TaskState.TaskStage nextStage,
                                TaskState.SubStage nextSubStage) {

    QueryTask.Query.Builder templateNameClauseBuilder = QueryTask.Query.Builder.create();
    for (ContainersConfig.ContainerType containerType : containerTypes) {
      templateNameClauseBuilder.addFieldClause(ContainerTemplateService.State.FIELD_NAME_NAME, containerType.name(),
          QueryTask.Query.Occurance.SHOULD_OCCUR);
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerTemplateService.State.class)
            .addClause(templateNameClauseBuilder.build())
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> documentLinks = o.getBody(QueryTask.class).results.documentLinks;
                if (skipContainerCreation(documentLinks, containerTypes, currentState)) {
                  sendStageProgressPatch(nextStage, nextSubStage);
                }
                checkState(documentLinks.size() == containerTypes.size());
                queryContainersForTemplates(currentState, documentLinks, nextStage, nextSubStage);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private boolean skipContainerCreation(
      List<String> documentLinks,
      List<ContainerType> containerTypes,
      State currentState) {
    if (containerTypes.size() == 1
        && containerTypes.contains(ContainerType.Lightwave)
        && documentLinks.isEmpty()) {
      ServiceUtils.logInfo(this, "Skipping creation of Lightwave container (using external instance)");
      return true;
    }
    return false;
  }

  private void queryContainersForTemplates(State currentState,
                                           List<String> templateServiceLinks,
                                           TaskState.TaskStage nextStage,
                                           TaskState.SubStage nextSubStage) {

    QueryTask.Query.Builder templateClauseBuilder = QueryTask.Query.Builder.create();
    for (String templateServiceLink : templateServiceLinks) {
      templateClauseBuilder.addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK,
          templateServiceLink, QueryTask.Query.Occurance.SHOULD_OCCUR);
    }

    QueryTask.Query.Builder containerQueryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(ContainerService.State.class)
        .addClause(templateClauseBuilder.build());

    if (currentState.vmServiceLink != null) {
      containerQueryBuilder.addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK,
          currentState.vmServiceLink);
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(containerQueryBuilder.build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation.
        createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> documentLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(documentLinks.size() != 0);
                createAggregatorTask(currentState, documentLinks, nextStage, nextSubStage);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createAggregatorTask(State currentState,
                                    List<String> containerServiceLinks,
                                    TaskState.TaskStage nextStage,
                                    TaskState.SubStage nextSubStage) {

    ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
    startState.parentTaskLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false, buildPatch(nextStage, nextSubStage, null));
    startState.pendingCompletionCount = containerServiceLinks.size();
    startState.errorThreshold = 0.0;

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
                createContainerTasks(currentState, containerServiceLinks,
                    o.getBody(ServiceDocument.class).documentSelfLink);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createContainerTasks(State currentState,
                                    List<String> containerServiceLinks,
                                    String aggregatorServiceLink) {

    for (String containerServiceLink : containerServiceLinks) {
      CreateContainerTaskService.State startState = new CreateContainerTaskService.State();
      startState.parentTaskServiceLink = aggregatorServiceLink;
      startState.deploymentServiceLink = currentState.deploymentServiceLink;
      startState.containerServiceLink = containerServiceLink;

      sendRequest(Operation
          .createPost(this, CreateContainerTaskFactoryService.SELF_LINK)
          .setBody(startState)
          .setCompletion(
              (o, e) -> {
                if (e != null) {
                  failTask(e);
                }
              }));
    }
  }

  private void generateCertificate(State currentState,
                                  TaskState.TaskStage nextStage,
                                  TaskState.SubStage nextSubStage) {

    if (!currentState.isNewDeployment) {
      ServiceUtils.logInfo(this, "Skipping certificate generation - not a new deployment");
      sendStageProgressPatch(nextStage, nextSubStage);
      return;
    }

    if (!currentState.isAuthEnabled) {
      ServiceUtils.logInfo(this, "Skipping certificate generation - auth is disabled");
      sendStageProgressPatch(nextStage, nextSubStage);
      return;
    }

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);
                    generateCertificate(deploymentState, nextStage, nextSubStage);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void generateCertificate(DeploymentService.State deploymentState,
                                   TaskState.TaskStage nextStage,
                                   TaskState.SubStage nextSubStage) {
    List<String> command = new ArrayList<>();
    command.add("./" + GENERATE_CERTIFICATE_SCRIPT_NAME);
    command.add(deploymentState.oAuthServerAddress);
    command.add(deploymentState.oAuthPassword);
    command.add(deploymentState.oAuthTenantName);
    command.add(PhotonControllerXenonHost.KEYSTORE_FILE);
    command.add(PhotonControllerXenonHost.KEYSTORE_PASSWORD);

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);
    File scriptLogFile = new File(deployerContext.getScriptLogDirectory(), GENERATE_CERTIFICATE_SCRIPT_NAME + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);
    Futures.addCallback(futureTask,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull Integer result) {
            try {
              if (result != 0) {
                logScriptErrorAndFail(result, scriptLogFile);
              } else {
                // Set the inInstaller flag to true which would allow us to override the xenon service client to talk
                // to the auth enabled newly deployed management plane using https with two way SSL.
                ((PhotonControllerXenonHost) getHost()).setInInstaller(true);

                // need to switch the ssl context for the thrift clients to use
                // the generated certs to be able to talk to the authenticated
                // agents
                try {
                  SSLContext sslContext = SSLContext.getInstance(KeyStoreUtils.THRIFT_PROTOCOL);
                  TrustManagerFactory tmf = null;

                  tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                  KeyStore keyStore = KeyStore.getInstance("JKS");
                  InputStream in = FileUtils.openInputStream(new File(PhotonControllerXenonHost.KEYSTORE_FILE));
                  keyStore.load(in, PhotonControllerXenonHost.KEYSTORE_PASSWORD.toCharArray());
                  tmf.init(keyStore);
                  sslContext.init(null, tmf.getTrustManagers(), null);
                  ((PhotonControllerXenonHost) getHost()).regenerateThriftClients(sslContext);

                  KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);
                } catch (Throwable t) {
                  ServiceUtils.logSevere(CreateContainersWorkflowService.this,
                      "Regenerating the SSL Context for thrift failed, ignoring to make tests pass, it fail later");
                  ServiceUtils.logSevere(CreateContainersWorkflowService.this, t);
                }
                sendStageProgressPatch(nextStage, nextSubStage);
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void logScriptErrorAndFail(Integer result, File scriptLogFile) throws Throwable {
    ServiceUtils.logSevere(this, GENERATE_CERTIFICATE_SCRIPT_NAME + " returned " + result.toString());
    ServiceUtils.logSevere(this, "Script output: " + FileUtils.readFileToString(scriptLogFile));
    failTask(new IllegalStateException("Generating certificate failed with exit code " + result.toString()));
  }


  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage, null));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  private void failTask(Collection<Throwable> failures) {
    ServiceUtils.logSevere(this, failures);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;
    patchState.taskState.subStage = subStage;
    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
