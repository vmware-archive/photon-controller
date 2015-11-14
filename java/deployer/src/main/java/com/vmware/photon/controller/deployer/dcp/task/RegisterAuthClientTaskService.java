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
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactoryProvider;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * This class implements a DCP micro-service that registers a client to lotus.
 */
public class RegisterAuthClientTaskService extends StatefulService {

  /**
   * Class defines the state of the AuthClientRegsitrationService.
   */
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

    @NotNull
    @Immutable
    public String deploymentServiceLink;
  }

  public RegisterAuthClientTaskService() {
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
        queryLoadBalancerContainerTemplate(currentState);
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

  private static final String AUTH_ADMIN_USER_NAME = "administrator";

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
   * Get the container template for load balancer.
   *
   * @param currentState
   * @throws Throwable
   */
  private void queryLoadBalancerContainerTemplate(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(ContainersConfig.ContainerType.LoadBalancer.name());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(RegisterAuthClientTaskService.this, documentLinks);
              checkState(1 == documentLinks.size());
              queryLoadBalancerContainer(currentState, documentLinks.iterator().next());
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryLoadBalancerContainer(final State currentState, String containerTemplateServiceLink) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
        .setTermMatchValue(containerTemplateServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(RegisterAuthClientTaskService.this, documentLinks);
              checkState(1 == documentLinks.size());
              getLoadBalancerContainerEntity(currentState, documentLinks.iterator().next());
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getLoadBalancerContainerEntity(final State currentState, final String containerServiceLink) {

    Operation getOperation = Operation
        .createGet(this, containerServiceLink)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              ContainerService.State containerState = operation.getBody(ContainerService.State.class);
              getLoadBalancerVmEntity(currentState, containerState.vmServiceLink);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(getOperation);
  }

  private void getLoadBalancerVmEntity(final State currentState, String vmServiceLink) {

    Operation getOperation = Operation
        .createGet(this, vmServiceLink)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              VmService.State vmState = operation.getBody(VmService.State.class);
              getDeploymentDocuments(currentState, vmState.ipAddress);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(getOperation);
  }

  /**
   * Read the documents carried by patch state.
   *
   * @param currentState Current State.
   * @param lbIpAddress IP address of the load balancer.
   */
  private void getDeploymentDocuments(final State currentState, final String lbIpAddress) throws Throwable {

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
                    registerAuthClient(deploymentState, currentState.deploymentServiceLink, lbIpAddress);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
   * Register a client to Lotus, and then produce the URL to access it.
   *
   * @param deploymentState       State of deployment.
   * @param deploymentServiceLink Link to the deployment service instance.
   * @param lbIpAddress           IP address of the load balancer.
   */
  private void registerAuthClient(final DeploymentService.State deploymentState,
                                  final String deploymentServiceLink,
                                  final String lbIpAddress) throws Throwable {
    AuthHelperFactory authHelperFactory = ((AuthHelperFactoryProvider) getHost()).getAuthHelperFactory();
    final AuthHelper authHelper = authHelperFactory.create();

    ServiceUtils.logInfo(this, "Starting a thread to register client %s at %s:%s using user %s on tenant %s.",
        lbIpAddress,
        deploymentState.oAuthServerAddress,
        deploymentState.oAuthServerPort,
        AUTH_ADMIN_USER_NAME,
        deploymentState.oAuthTenantName);

    //
    // Lightwave requires login name to be in format "domain/user"
    //
    String loginName = deploymentState.oAuthTenantName + "\\" + AUTH_ADMIN_USER_NAME;
    ListenableFutureTask futureTask = ListenableFutureTask.create(new Callable() {
      @Override
      public Object call() throws Exception {
        return authHelper.getResourceLoginUri(deploymentState.oAuthTenantName,
            loginName,
            deploymentState.oAuthPassword,
            deploymentState.oAuthServerAddress,
            deploymentState.oAuthServerPort,
            Constants.getSwaggerUiLoginRedirectPage(lbIpAddress),
            Constants.getSwaggerUiLogoutRedirectPage(lbIpAddress));
      }
    });

    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<AuthClientHandler.ImplicitClient> futureCallback =
        new FutureCallback<AuthClientHandler.ImplicitClient>() {
      @Override
      public void onSuccess(AuthClientHandler.ImplicitClient result) {
        DeploymentService.State patchState = new DeploymentService.State();
        patchState.oAuthResourceLoginEndpoint = result.loginURI;
        patchState.oAuthLogoutEndpoint = result.logoutURI;
        sendDeploymentPatch(patchState, deploymentServiceLink);
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  /**
   * Sends a patch operation to the given deployment service instance.
   *
   * @param deploymentPatchState  Deployment state.
   * @param deploymentServicelink Link to the deployment service.
   */
  private void sendDeploymentPatch(DeploymentService.State deploymentPatchState, String deploymentServicelink) {
    CloudStoreHelper cloudStoreHelper = ((DeployerDcpServiceHost) getHost()).getCloudStoreHelper();
    cloudStoreHelper.patchEntity(this, deploymentServicelink, deploymentPatchState, new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        sendStageProgressPatch(TaskState.TaskStage.FINISHED);
      }
    });
  }
}
