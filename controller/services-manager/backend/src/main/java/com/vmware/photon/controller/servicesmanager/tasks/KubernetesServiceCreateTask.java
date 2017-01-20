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
package com.vmware.photon.controller.servicesmanager.tasks;

import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.servicesmanager.rolloutplans.WorkersNodeRollout;
import com.vmware.photon.controller.servicesmanager.servicedocuments.KubernetesServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.KubernetesServiceCreateTaskState.TaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesEtcdNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesMasterNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * This class implements a Xenon service representing a task to create a Kubernetes service.
 */
public class KubernetesServiceCreateTask extends StatefulService {

  private static final int MINIMUM_INITIAL_WORKER_COUNT = 1;

  public KubernetesServiceCreateTask() {
    super(KubernetesServiceCreateTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    KubernetesServiceCreateTaskState startState = start.getBody(KubernetesServiceCreateTaskState.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.SETUP_ETCD;
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
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, TaskState.SubStage.SETUP_ETCD));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    KubernetesServiceCreateTaskState currentState = getState(patch);
    KubernetesServiceCreateTaskState patchState = patch.getBody(KubernetesServiceCreateTaskState.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(KubernetesServiceCreateTaskState currentState) throws IOException {
    ServiceUtils.logInfo(this, "Start %s with service id: %s", currentState.taskState.subStage, currentState.serviceId);
    switch (currentState.taskState.subStage) {
      case SETUP_ETCD:
        setupEtcds(currentState);
        break;

      case SETUP_MASTER:
        setupMaster(currentState);
        break;

      case UPDATE_EXTENDED_PROPERTIES:
        updateExtendedProperties(currentState);
        break;

      case SETUP_WORKERS:
        setupInitialWorkers(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method roll-outs Etcd nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_MASTER.
   *
   * @param currentState
   */
  private void setupEtcds(KubernetesServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.vmFlavorName = service.otherVmFlavorName;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.nodeCount = NodeTemplateUtils.deserializeAddressList(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)).size();
          rolloutInput.nodeType = NodeType.KubernetesEtcd;
          rolloutInput.nodeProperties = KubernetesEtcdNodeTemplate.createProperties(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK),
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  KubernetesServiceCreateTask.this,
                  buildPatch(KubernetesServiceCreateTaskState.TaskState.TaskStage.STARTED,
                      KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.KubernetesEtcd, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs Kubernetes Master Nodes. On successful roll-out,
   * the methods moves the task sub-stage to SETUP_WORKERS.
   *
   * @param currentState
   */
  private void setupMaster(final KubernetesServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmFlavorName = service.masterVmFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.nodeCount = ServicesManagerConstants.Kubernetes.MASTER_COUNT;
          rolloutInput.nodeType = NodeType.KubernetesMaster;
          rolloutInput.nodeProperties = KubernetesMasterNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  KubernetesServiceCreateTask.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_EXTENDED_PROPERTIES));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.KubernetesMaster, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs the initial Kubernetes Worker Nodes. On successful roll-out,
   * the method creates necessary tasks for service maintenance.
   *
   * @param currentState
   */
  private void setupInitialWorkers(final KubernetesServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmFlavorName = service.otherVmFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.nodeCount = MINIMUM_INITIAL_WORKER_COUNT;
          rolloutInput.nodeType = NodeType.KubernetesWorker;
          rolloutInput.serverAddress =
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
          rolloutInput.nodeProperties = KubernetesWorkerNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE));

          NodeRollout rollout = new WorkersNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              setupRemainingWorkers(currentState, service);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.KubernetesWorker, t);
            }
          });
        }));
  }

  private void setupRemainingWorkers(
      final KubernetesServiceCreateTaskState currentState,
      final ServiceState.State service) {
    // Maintenance task should be singleton for any service.
    ServiceMaintenanceTask.State startState = new ServiceMaintenanceTask.State();
    startState.batchExpansionSize = currentState.workerBatchExpansionSize;
    startState.documentSelfLink = currentState.serviceId;

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceMaintenanceTaskFactory.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.KubernetesWorker, throwable);
            return;
          }
          if (service.workerCount == MINIMUM_INITIAL_WORKER_COUNT) {
            // We short circuit here and set the serviceState as READY, since the desired size has
            // already been reached. Maintenance will kick-in when the maintenance interval elapses.
            KubernetesServiceCreateTaskState patchState = buildPatch(TaskState.TaskStage.FINISHED, null);

            ServiceState.State servicePatch = new ServiceState.State();
            servicePatch.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;

            updateStates(currentState, patchState, servicePatch);
          } else {
            // The handleStart method of the maintenance task does not push itself to STARTED automatically.
            // We need to patch the maintenance task manually to start the task immediately. Otherwise
            // the task will wait for one interval to start.
            startMaintenance(currentState);
          }
        });
    sendRequest(postOperation);
  }

  /**
   * This method patches new extended properties. Version, UI and addresses to download kubectl.
   *
   * @param currentState
   */
  private void updateExtendedProperties(final KubernetesServiceCreateTaskState currentState) {

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(UriUtils.buildUriPath(ServiceStateFactory.SELF_LINK, currentState.serviceId))
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }
          ServiceState.State service = operation.getBody(ServiceState.State.class);
          // Retrieving the masterIP from service Service document
          String masterIP = service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
          KubernetesClient kubeClient = HostUtils.getKubernetesClient(this);
          // Create the connectionstring used by the Kubernetes Client
          String connectionString = createKubernetesURL(masterIP, null);
          try {
            getVersionAndUpdate(kubeClient, connectionString, masterIP, currentState);
          } catch (Exception e) {
            failTask(e);
          }
        }));
  }

  // This is a helper method which takes in a kubernetes Client, get the version of the current kubernetes
  // and construct a map which contains all the extra extended properties we want to add.
  private void getVersionAndUpdate(KubernetesClient kubeClient, String connectionString, String masterIP,
                                   final KubernetesServiceCreateTaskState currentState) throws IOException{
    kubeClient.getVersionAsync(connectionString, new FutureCallback<String>() {
      @Override
      public void onSuccess(@Nullable String version) {
        Map<String, String> extraInfo = new HashMap<String, String>();
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_SERVICE_VERSION, version);
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_SERVICE_UI_URL, createKubernetesURL(masterIP, "ui"));
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_CLIENT_LINUX_AMD64_URL,
            generateKubectlDownloadAddress(version, "linux", "amd64"));
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_CLIENT_LINUX_386_URL,
            generateKubectlDownloadAddress(version, "linux", "386"));
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_CLIENT_DARWIN_AMD64_URL,
            generateKubectlDownloadAddress(version, "darwin", "amd64"));
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_CLIENT_WINDOWS_AMD64_URL,
            generateKubectlDownloadAddress(version, "windows", "amd64"));
        extraInfo.put(ServicesManagerConstants.EXTENDED_PROPERTY_CLIENT_WINDOWS_386_URL,
            generateKubectlDownloadAddress(version, "windows", "386"));

        updateExtendedMap(extraInfo, currentState);
      }

      @Override
      public void onFailure(Throwable t) {
        // Sometimes queries to Kubernetes fail, even after it comes up.
        // To be resilient to intermittent failures, we retry upto maxPollIterations times.
        // The retry interval is defined by pollDelay.
        if (currentState.versionPollIterations >= currentState.versionMaxPollIterations) {
          failTask(t);
        } else {
          getHost().schedule(
              () -> {
                KubernetesServiceCreateTaskState patchState = buildPatch(TaskState.TaskStage.STARTED,
                    TaskState.SubStage.UPDATE_EXTENDED_PROPERTIES);
                patchState.versionPollIterations = currentState.versionPollIterations + 1;
                TaskUtils.sendSelfPatch(KubernetesServiceCreateTask.this, patchState);
              },
              currentState.versionPollDelay,
              TimeUnit.MILLISECONDS);
        }
      }
    });
  }

  // Taking the map that contains all the extra extended property we want to add. Add them and send a self patch
  // Indicating that there are no future substage as well as patching to the service Service,
  private void updateExtendedMap(Map<String, String> extendedPatchMap,
                                 final KubernetesServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(UriUtils.buildUriPath(ServiceStateFactory.SELF_LINK, currentState.serviceId))
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }
          // Get the service
          ServiceState.State service = operation.getBody(ServiceState.State.class);
          // Adding all the existing extendedProperties in to my map
          for (String key : service.extendedProperties.keySet()) {
            extendedPatchMap.put(key, service.extendedProperties.get(key));
          }
          // Create a new service containing the patch(only has the map)
          ServiceState.State servicePatch = new ServiceState.State();
          servicePatch.extendedProperties = extendedPatchMap;
          // Go to setup worker in the State machine
          KubernetesServiceCreateTaskState patchState = buildPatch(TaskState.TaskStage.STARTED,
              TaskState.SubStage.SETUP_WORKERS);
          updateStates(currentState, patchState, servicePatch);
        }));
  }

  // Generate the URL for the Kubernetes API. if you pass other to be null, by default, this method will generate
  // the kubernetes path with a formate of http://serverAddress:8080
  // You can append any specific path in other. For example, if you pass ui in other, then the output would be
  // http://serverAddress:8080/other
  private String createKubernetesURL(String serverAddress, String other) {
    String url = "";
    url = UriUtils.buildUri(UriUtils.HTTP_SCHEME, serverAddress,
        ServicesManagerConstants.Kubernetes.API_PORT, other, null).toString();
    ServiceUtils.logInfo(this, "URL passed to retrieve version of current kubernetes" + url);
    return url;
  }

  // Generate the URL for downloading kubectl
  private String generateKubectlDownloadAddress(String version, String os, String model) {
    // For windows url, there is a .exe in the end
    String path = "";
    if (os.equals("windows")) {
      path = UriUtils.buildUriPath(ServicesManagerConstants.KUBECTL_PATH_RELEASE, version,
          ServicesManagerConstants.BIN, os, model, ServicesManagerConstants.KUBECTLEXE);
    } else {
      path = UriUtils.buildUriPath(ServicesManagerConstants.KUBECTL_PATH_RELEASE, version,
          ServicesManagerConstants.BIN, os, model, ServicesManagerConstants.KUBECTL);
    }
    return UriUtils.buildUri(UriUtils.HTTPS_SCHEME, ServicesManagerConstants.KUBECTL_BASE_URI, 443, path, null)
        .toString();
  }

  private void startMaintenance(final KubernetesServiceCreateTaskState currentState) {
    ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ServiceMaintenanceTaskFactory.SELF_LINK + "/" + currentState.serviceId))
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          // We ignore the failure here since maintenance task will kick in eventually.
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        });
    sendRequest(patchOperation);
  }

  private void validateStartState(KubernetesServiceCreateTaskState startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case SETUP_ETCD:
        case SETUP_MASTER:
        case UPDATE_EXTENDED_PROPERTIES:
        case SETUP_WORKERS:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(KubernetesServiceCreateTaskState currentState,
                                  KubernetesServiceCreateTaskState patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private KubernetesServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private KubernetesServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage,
                                                 @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private KubernetesServiceCreateTaskState buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    KubernetesServiceCreateTaskState state = new KubernetesServiceCreateTaskState();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final KubernetesServiceCreateTaskState currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    KubernetesServiceCreateTaskState patchState = buildPatch(
        TaskState.TaskStage.FAILED, null,
        new IllegalStateException(String.format("Failed to rollout %s. Error: %s",
            nodeType.toString(), throwable.toString())));

    ServiceState.State document = new ServiceState.State();
    document.serviceState = com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR;
    // Up date the errorReason so that when people later query this service, they can see why it failed
    document.errorReason = throwable.toString();
    updateStates(currentState, patchState, document);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void updateStates(final KubernetesServiceCreateTaskState currentState,
                            final KubernetesServiceCreateTaskState patchState,
                            final ServiceState.State servicePatchState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
            .setBody(servicePatchState)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  TaskUtils.sendSelfPatch(KubernetesServiceCreateTask.this, patchState);
                }
            ));
  }
}
