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

import com.vmware.photon.controller.common.logging.LoggingUtils;
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

import java.util.UUID;

/**
 * The main responsibility of this class is to communicate with DHCP server to lease IP
 * provided in the patch. This service will be invoked for each Subnet.
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
            } else if (startState.subnetIPLease.subnetOperation == SubnetIPLease.SubnetOperation.UPDATE) {
                handleUpdateSubnetIPLease(startState, start);
            } else if (startState.subnetIPLease.subnetOperation == SubnetIPLease.SubnetOperation.DELETE) {
                handleDeleteSubnetIPLease(start);
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
            } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage
                    && SubnetIPLease.SubnetOperation.UPDATE == currentState.subnetIPLease.subnetOperation) {
                handleUpdateSubnetIPLease(currentState, patchOperation);
            } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage
                    && SubnetIPLease.SubnetOperation.DELETE == currentState.subnetIPLease.subnetOperation) {
                handleDeleteSubnetIPLease(patchOperation);
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
     * @param postOperation
     */
    public void handleUpdateSubnetIPLease(SubnetIPLeaseTask currentState, Operation postOperation) {
        initRequestId(currentState);

        try {
            ((DHCPAgentXenonHost) getHost()).getDHCPDriver().updateSubnetIPLease(
                    currentState.subnetIPLease.subnetId,
                    currentState.subnetIPLease.ipToMACAddressMap);

            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            patchState.requestId = currentState.requestId;
            patchState.subnetIPLease = new SubnetIPLease();
            patchState.subnetIPLease.subnetId = currentState.subnetIPLease.subnetId;
            patchState.subnetIPLease.ipToMACAddressMap = currentState.subnetIPLease.ipToMACAddressMap;
            sendPatch(patchState, postOperation);
        } catch (Throwable ex) {
            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
            patchState.requestId = currentState.requestId;
            failWithThrowable(patchState, ex, postOperation);
        }
    }

    /**
     * This method generates request to DHCP agent for
     * deleting IP leases for subnet to cleanup network resources.
     *
     * @param postOperation
     */
    public void handleDeleteSubnetIPLease(Operation postOperation) {
        SubnetIPLeaseTask currentState = getState(postOperation);
        initRequestId(currentState);

        try {
            ((DHCPAgentXenonHost) getHost()).getDHCPDriver().deleteSubnetIPLease(currentState.subnetIPLease.subnetId);

            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            patchState.requestId = currentState.requestId;
            sendPatch(patchState, postOperation);
        } catch (Throwable ex) {
            SubnetIPLeaseTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
            patchState.requestId = currentState.requestId;
            failWithThrowable(patchState, ex, postOperation);
        }
    }

    /**
     * Sending an update to itself.
     * @param patchState the SubnetIPLeaseTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void sendPatch(SubnetIPLeaseTask patchState, Operation postOperation) {
        if (postOperation == null) {
            TaskUtils.sendSelfPatch(this, patchState);
        } else {
            postOperation.setBody(patchState).complete();
        }
    }

    /**
     * This reports the error that caused the failure state of patchState before sending an update
     * to itself.
     * @param patchState the failed SubnetIPLeaseTask
     * @param t the error associated with the failed SubnetIPLeaseTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void failTask(SubnetIPLeaseTask patchState, Throwable t, Operation postOperation) {
        ServiceUtils.logSevere(this, t);
        sendPatch(patchState, postOperation);
    }

    /**
     * This builds the patch and sends it to itself in case of failure.
     * to itself.
     * @param currentState the failed SubnetIPLeaseTask
     * @param throwable the error associated with the failed SubnetIPLeaseTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void failWithThrowable(SubnetIPLeaseTask currentState, Throwable throwable, Operation postOperation) {
        failTask(currentState, new Throwable(throwable), postOperation);
    }

    /**
     * Sets a unique request id for the SubnetIPLeaseTask if it has not been set. This logs the
     * request id as it completes its operation.
     * @param currentState the SubnetIPLeaseTask
     */
    private static void initRequestId(SubnetIPLeaseTask currentState) {
        if (currentState.requestId == null) {
            currentState.requestId = UUID.randomUUID().toString();
        }
        LoggingUtils.setRequestId(currentState.requestId);
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
