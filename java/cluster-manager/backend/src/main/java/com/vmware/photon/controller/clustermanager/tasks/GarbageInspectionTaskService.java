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
package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmFactoryService;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmService;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.statuschecks.SlavesStatusChecker;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a Xenon service representing a task to inspect cluster for inactive slave vms.
 */
public class GarbageInspectionTaskService extends StatefulService {

  public GarbageInspectionTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
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
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getClusterState(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * Call cloud store to get cluster state.
   *
   * @param currentState
   */
  private void getClusterState(final State currentState) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }
                  ClusterService.State clusterState = operation.getBody(ClusterService.State.class);
                  getVmsFromApi(currentState, clusterState);
                }
            ));
  }

  /**
   * Call api-fe to get slave vms.
   *
   * @param currentState
   */
  private void getVmsFromApi(final State currentState, final ClusterService.State clusterState) {
    try {
      HostUtils.getApiClient(this).getClusterApi().getVmsInClusterAsync(
          currentState.clusterId,
          new FutureCallback<ResourceList<Vm>>() {
            @Override
            public void onSuccess(@Nullable ResourceList<Vm> result) {
              try {
                String masterNodeTag;
                String slaveNodeTag;
                switch (clusterState.clusterType) {
                  case KUBERNETES:
                    masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.KubernetesMaster);
                    slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.KubernetesSlave);
                    break;
                  case MESOS:
                    masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.MesosMaster);
                    slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.MesosSlave);
                    break;
                  case SWARM:
                    masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.SwarmMaster);
                    slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.SwarmSlave);
                    break;
                  default:
                    throw new UnsupportedOperationException(
                        "ClusterType is not supported. ClusterType: " + clusterState.clusterType);
                }

                String masterVmId = null;
                Set<Vm> slaveNodes = new HashSet<>();
                for (Vm vm : result.getItems()) {
                  if (vm.getTags().contains(slaveNodeTag)) {
                    slaveNodes.add(vm);
                  } else if (vm.getTags().contains(masterNodeTag)) {
                    if (masterVmId == null) {
                      masterVmId = vm.getId();
                    }
                  }
                }
                Preconditions.checkNotNull(masterVmId, "No master vm is found.");

                getMasterIp(currentState, clusterState, masterVmId, slaveNodes);

              } catch (Throwable t) {
                failTask(t);
              }
            }

            @Override
            public void onFailure(Throwable t) {
              failTask(t);
            }
          }
      );
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getMasterIp(final State currentState,
                           final ClusterService.State clusterState,
                           final String masterVmId,
                           final Set<Vm> allSlaves) {
    WaitForNetworkTaskService.State startState = new WaitForNetworkTaskService.State();
    startState.vmId = masterVmId;

    TaskUtils.startTaskAsync(
        this,
        WaitForNetworkTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        WaitForNetworkTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<WaitForNetworkTaskService.State>() {
          @Override
          public void onSuccess(@Nullable WaitForNetworkTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  getSlavesFromMaster(currentState, clusterState, result.vmIpAddress, allSlaves);
                } catch (Throwable t) {
                  failTask(t);
                }
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(GarbageInspectionTaskService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED));
                break;
              case FAILED:
                TaskUtils.sendSelfPatch(GarbageInspectionTaskService.this,
                    buildPatch(TaskState.TaskStage.FAILED, result.taskState.failure));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  /**
   * Call cluster masters to get slave nodes.
   *
   * @param currentState
   */
  private void getSlavesFromMaster(final State currentState,
                                   final ClusterService.State clusterState,
                                   final String masterIp,
                                   final Set<Vm> allSlaves) {

    PhotonControllerXenonHost photonControllerXenonHost = (PhotonControllerXenonHost) getHost();
    ClusterManagerFactory clusterManagerFactory =
        ((ClusterManagerFactoryProvider) photonControllerXenonHost.getDeployer()).getClusterManagerFactory();
    StatusCheckHelper helper = clusterManagerFactory.createStatusCheckHelper();
    SlavesStatusChecker checker;
    switch (clusterState.clusterType) {
      case KUBERNETES:
        checker = helper.createSlavesStatusChecker(this, NodeType.KubernetesSlave);
        break;
      case MESOS:
        checker = helper.createSlavesStatusChecker(this, NodeType.MesosSlave);
        break;
      case SWARM:
        checker = helper.createSlavesStatusChecker(this, NodeType.SwarmSlave);
        break;
      default:
        failTask(new UnsupportedOperationException(
            "ClusterType is not supported. ClusterType: " + clusterState.clusterType));
        return;
    }
    checker.getSlavesStatus(masterIp, new FutureCallback<Set<String>>() {
          @Override
          public void onSuccess(@Nullable Set<String> activeNodes) {
            try {
              Preconditions.checkNotNull(activeNodes);

              // Calculate inactive vms
              Set<String> inactiveVms = new HashSet<>();
              for (Vm vm : allSlaves) {
                if (activeNodes.contains(vm.getName())) {
                  continue;
                }
                inactiveVms.add(vm.getId());
              }

              if (inactiveVms.size() > 0) {
                createInactiveVmEntities(currentState.clusterId, inactiveVms);
              } else {
                TaskUtils.sendSelfPatch(GarbageInspectionTaskService.this,
                    buildPatch(TaskState.TaskStage.FINISHED));
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void createInactiveVmEntities(String clusterId, Set<String> inactiveVmIds) {

    OperationJoin
        .create(inactiveVmIds.stream()
            .map((String vmId) -> {
              InactiveVmService.State inactiveVm = new InactiveVmService.State();
              inactiveVm.clusterId = clusterId;
              inactiveVm.documentSelfLink = vmId; // use vmId as InactiveVmEntity id.
              return Operation.createPost(this, InactiveVmFactoryService.SELF_LINK).setBody(inactiveVm);
            }))
        .setCompletion((Map<Long, Operation> ops, Map<Long, Throwable> exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
          } else {
            TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED));
          }
        })
        .sendWith(this);
  }

  private void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private State buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private State buildPatch(
      TaskState.TaskStage stage, @Nullable Throwable t) {
    return buildPatch(stage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private State buildPatch(
      TaskState.TaskStage stage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach((Throwable e) -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }

  /**
   * This class represents the document state associated with a
   * {@link GarbageInspectionTaskService} task.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * The state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the identifier of the cluster.
     */
    @NotNull
    @Immutable
    public String clusterId;
  }
}
