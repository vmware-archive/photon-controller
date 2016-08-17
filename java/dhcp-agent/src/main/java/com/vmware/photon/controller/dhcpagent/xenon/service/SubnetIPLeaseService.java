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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.dhcpagent.xenon.DHCPAgentXenonHost;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The main responsibility of this class is to communicate with DHCP server IP leases
 * for a subnet provided in the patch. This service will be invoked for each Subnet.
 */
public class SubnetIPLeaseService extends StatefulService {

    public static final String FACTORY_LINK = ServiceUriPaths.DHCPAGENT_ROOT + "/subnetiplease";

    /**
     * This class implements a Xenon micro-service that provides a factory for
     * {@link com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseService} instances.
     */
    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(
                com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseService.class);
    }

    public SubnetIPLeaseService() {
        super(SubnetIPLeaseTask.class);

        // The service handles each task of subnet as a single request so there is no need for
        // multiple nodes to have the same information nor a specific node to be the leader of
        // this operation. Persistence is not needed since on a failure subnet IP lease will
        // be retried.
        super.toggleOption(ServiceOption.PERSISTENCE, false);
        super.toggleOption(ServiceOption.REPLICATION, false);
        super.toggleOption(ServiceOption.OWNER_SELECTION, false);
    }

    @Override
    public void handleStart(Operation start) {
        ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

        SubnetIPLeaseTask startState = start.getBody(SubnetIPLeaseTask.class);
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
                return;
            }

            if (startState.subnetIPLease.subnetOperation == SubnetIPLeaseTask.SubnetOperation.UPDATE) {
                handleUpdateSubnetIPLease(startState, start);
            } else if (startState.subnetIPLease.subnetOperation == SubnetIPLeaseTask.SubnetOperation.DELETE) {
                handleDeleteSubnetIPLease(startState, start);
            }
        } catch (Throwable t) {
            failTask(buildPatch(TaskState.TaskStage.FAILED, t), t, start);
        }
    }

    @Override
    public void handlePatch(Operation patchOperation) {
        ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

        SubnetIPLeaseTask currentState = getState(patchOperation);
        SubnetIPLeaseTask patchState = patchOperation.getBody(SubnetIPLeaseTask.class);
        validatePatchState(currentState, patchState);
        PatchUtils.patchState(currentState, patchState);
        validateState(currentState);
        patchOperation.complete();

        try {
            if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
                ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
                return;
            }

            if (TaskState.TaskStage.STARTED == currentState.taskState.stage
                    && SubnetIPLeaseTask.SubnetOperation.UPDATE == currentState.subnetIPLease.subnetOperation) {
                handleUpdateSubnetIPLease(currentState, patchOperation);
            } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage
                    && SubnetIPLeaseTask.SubnetOperation.DELETE == currentState.subnetIPLease.subnetOperation) {
                handleDeleteSubnetIPLease(currentState, patchOperation);
            }
        } catch (Throwable t) {
            failTask(buildPatch(TaskState.TaskStage.FAILED, t), t, null);
        }
    }

    private void validateState(SubnetIPLeaseTask state) {
        ValidationUtils.validateState(state);
        ValidationUtils.validateTaskStage(state.taskState);

        checkArgument(state.subnetIPLease.subnetId != null,
                "subnetId field cannot be null in a patch");
    }

    private void validatePatchState(SubnetIPLeaseTask currentState, SubnetIPLeaseTask patchState) {
        ValidationUtils.validatePatch(currentState, patchState);
        ValidationUtils.validateTaskStage(patchState.taskState);
        ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
    }

    /**
     * This method generates request to DHCP agent for
     * updating IP leases for the subnet.
     *
     * @param currentState
     * @param patchOperation
     */
    public void handleUpdateSubnetIPLease(SubnetIPLeaseTask currentState, Operation patchOperation) {
        try {
            ((DHCPAgentXenonHost) getHost()).getDHCPDriver().updateSubnetIPLease(
                    currentState.subnetIPLease.subnetId,
                    currentState.subnetIPLease.ipToMACAddressMap);

            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            if (patchOperation == null) {
                TaskUtils.sendSelfPatch(this, patchState);
            } else {
                patchOperation.setBody(patchState).complete();
            }

        } catch (Throwable ex) {
            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
            failTask(patchState, ex, patchOperation);
        }
    }

    /**
     * This method generates request to DHCP agent for
     * deleting IP leases for subnet to cleanup network resources.
     *
     * @param currentState
     * @param patchOperation
     */
    public void handleDeleteSubnetIPLease(SubnetIPLeaseTask currentState, Operation patchOperation) {
        try {
            ((DHCPAgentXenonHost) getHost()).getDHCPDriver().deleteSubnetIPLease(currentState.subnetIPLease.subnetId);

            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            if (patchOperation == null) {
                TaskUtils.sendSelfPatch(this, patchState);
            } else {
                patchOperation.setBody(patchState).complete();
            }
        } catch (Throwable ex) {
            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
            failTask(patchState, ex, patchOperation);
        }
    }

    /**
     * This reports the error that caused the failure state of state before sending an update
     * to itself.
     * @param state the failed SubnetIPLeaseTask
     * @param t the error associated with the failed SubnetIPLeaseTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void failTask(SubnetIPLeaseTask state, Throwable t, Operation postOperation) {
        ServiceUtils.logSevere(this, t);

        if (postOperation == null) {
            TaskUtils.sendSelfPatch(this, state);
        } else {
            postOperation.setBody(state).complete();
        }
    }

    /**
     * Builds a new SubnetIPLeaseTask with the specified stage.
     * If Throwable t is set then the failure response is added to the task state.
     * @param patchStage the stage to set the created SubnetIPLeaseTask.
     * @param t the error associated with this SubnetIPLeaseTask, if one occurred.
     * @return
     */
    protected static SubnetIPLeaseTask buildPatch(TaskState.TaskStage patchStage, Throwable t) {
        SubnetIPLeaseTask state = new SubnetIPLeaseTask();
        state.taskState = new TaskState();
        state.taskState.stage = patchStage;

        if (null != t) {
            state.taskState.failure = Utils.toServiceErrorResponse(t);
        }

        return state;
    }
}
