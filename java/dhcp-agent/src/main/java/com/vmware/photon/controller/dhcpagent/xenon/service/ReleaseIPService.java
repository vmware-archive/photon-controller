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
import com.vmware.photon.controller.common.provider.ListeningExecutorServiceProvider;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.photon.controller.dhcpagent.xenon.DHCPAgentXenonHost;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * The main responsibility of this class it to communicate with DHCP agent to release IP
 * provided in the patch. This service will be invoked for each VM being deleted to
 * free up the networking resources i.e. IP address.
 */
public class ReleaseIPService extends StatefulService {

    public static final String FACTORY_LINK = ServiceUriPaths.DHCPAGENT_ROOT + "/releaseip";

    /**
     * This class implements a Xenon micro-service that provides a factory for
     * {@link ReleaseIPService} instances.
     */
    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ReleaseIPService.class);
    }

    public ReleaseIPService() {
        super(ReleaseIPTask.class);

        // The release IP service handles each task of IP release as a single request
        // so there is no need for multiple nodes to have the same information nor a specific node to be
        // the leader of this operation. Persistence is not needed since on a failure we will log the information
        // and VM delete is not blocked on it.
        super.toggleOption(ServiceOption.PERSISTENCE, false);
        super.toggleOption(ServiceOption.REPLICATION, false);
        super.toggleOption(ServiceOption.OWNER_SELECTION, false);
    }

    @Override
    public void handleStart(Operation start) {
        ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

        ReleaseIPTask startState = start.getBody(ReleaseIPTask.class);
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
                handleReleaseIPRequest(startState, start);
            } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
                start.setBody(startState).complete();
                TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.isDirect,
                        null));
            }
        } catch (Throwable t) {
            failTask(buildPatch(TaskState.TaskStage.FAILED, startState.taskState.isDirect, t), t, start);
        }
    }

    @Override
    public void handlePatch(Operation patchOperation) {
        ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

        ReleaseIPTask currentState = getState(patchOperation);
        ReleaseIPTask patchState = patchOperation.getBody(ReleaseIPTask.class);
        validatePatchState(currentState, patchState);
        PatchUtils.patchState(currentState, patchState);
        validateState(currentState);
        patchOperation.complete();

        try {
            if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
                ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
            } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
                handleReleaseIPRequest(currentState, null);
            }
        } catch (Throwable t) {
            failTask(buildPatch(TaskState.TaskStage.FAILED, false, t), t, null);
        }
    }

    private void validateState(ReleaseIPTask state) {
        ValidationUtils.validateState(state);
        if (!state.taskState.isDirect) {
            ValidationUtils.validateTaskStage(state.taskState);
        }

        checkArgument(state.networkInterface != null, "networkInterface field cannot be null in a patch");
        checkArgument(state.ipAddress != null, "ipAddress field cannot be null in a patch");
        checkArgument(state.macAddress != null, "macAddress field cannot be null in a patch");
    }

    private void validatePatchState(ReleaseIPTask currentState, ReleaseIPTask patchState) {
        ValidationUtils.validatePatch(currentState, patchState);
        ValidationUtils.validateTaskStage(patchState.taskState);
        ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
    }

    /**
     * This method generates an async request to DHCP agent for
     * release IP for cleanup of network resources.
     *
     * @param currentState
     * @param postOperation
     */
    private void handleReleaseIPRequest(ReleaseIPTask currentState, Operation postOperation) {
        initRequestId(currentState);
        try {
            ListenableFutureTask<DHCPDriver.Response> futureTask = ListenableFutureTask.create(
                    new Callable<DHCPDriver.Response>() {
                        @Override
                        public DHCPDriver.Response call() throws Exception {
                            return ((DHCPAgentXenonHost) getHost()).getDHCPDriver().releaseIP(
                                    currentState.networkInterface, currentState.macAddress);
                        }
                    });
            ((ListeningExecutorServiceProvider) getHost()).getListeningExecutorService().submit(futureTask);
            Futures.addCallback(futureTask,
                    new FutureCallback<DHCPDriver.Response>() {
                        @Override
                        public void onSuccess(@javax.validation.constraints.NotNull DHCPDriver.Response response) {
                            try {
                                if (response.exitCode == 0) {
                                    ReleaseIPTask patchState = buildPatch(TaskState.TaskStage.FINISHED,
                                            currentState.taskState.isDirect, null);
                                    patchState.response = response;
                                    patchState.requestId = currentState.requestId;
                                    sendPatch(patchState, postOperation);
                                } else {
                                    ReleaseIPTask patchState = buildPatch(TaskState.TaskStage.FAILED,
                                            currentState.taskState.isDirect, null);
                                    patchState.response = response;
                                    patchState.requestId = currentState.requestId;
                                    failTask(patchState, new Throwable(response.stdError), postOperation);
                                }
                            } catch (Throwable throwable) {
                                failWithThrowable(currentState, throwable, postOperation);
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            failWithThrowable(currentState, throwable, postOperation);
                        }
                    });
        } catch (Throwable throwable) {
            failWithThrowable(currentState, throwable, postOperation);
        }
    }

    /**
     * Sending an update to itself.
     * @param patchState the ReleaseIPTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void sendPatch(ReleaseIPTask patchState, Operation postOperation) {
        if (postOperation == null) {
            TaskUtils.sendSelfPatch(this, patchState);
        } else {
            postOperation.setBody(patchState).complete();
        }
    }

    /**
     * This reports the error that caused the failure state of patchState before sending an update
     * to itself.
     * @param patchState the failed ReleaseIPTask
     * @param t the error associated with the failed ReleaseIPTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void failTask(ReleaseIPTask patchState, Throwable t, Operation postOperation) {
        ServiceUtils.logSevere(this, t);
        sendPatch(patchState, postOperation);
    }

    /**
     * This builds the patch and sends it to itself in case of failure.
     * to itself.
     * @param currentState the failed ReleaseIPTask
     * @param throwable the error associated with the failed ReleaseIPTask
     * @param postOperation if there is a postOperation, this is part of a direct task and will return
     *                      once this update is complete, otherwise moves to a failed state
     */
    private void failWithThrowable(ReleaseIPTask currentState, Throwable throwable, Operation postOperation) {
        ReleaseIPTask patchState = buildPatch(TaskState.TaskStage.FAILED,
                currentState.taskState.isDirect, throwable);
        patchState.requestId = currentState.requestId;
        failTask(patchState, new Throwable(throwable), postOperation);
    }

    /**
     * Sets a unique request id for the ReleaseIPTask if it has not been set. This logs the
     * request id as it completes its operation.
     * @param currentState the ReleaseIPTask
     */
    private static void initRequestId(ReleaseIPTask currentState) {
        if (currentState.requestId == null) {
            currentState.requestId = UUID.randomUUID().toString();
        }
        LoggingUtils.setRequestId(currentState.requestId);
    }

    /**
     * Builds a new ReleaseIPTask with the specified stage and isDirect boolean.
     * If Throwable t is set then the failure response is added to the task state.
     * @param patchStage the stage to set the created ReleaseIPTask
     * @param isDirect boolean if the ReleaseIPTask is a direct operation.
     * @param t the error associated with this ReleaseIPTask, if one occurred.
     * @return
     */
    @VisibleForTesting
    protected static ReleaseIPTask buildPatch(TaskState.TaskStage patchStage, boolean isDirect,
                                              Throwable t) {
        ReleaseIPTask state = new ReleaseIPTask();
        state.taskState = new TaskState();
        state.taskState.stage = patchStage;
        state.taskState.isDirect = isDirect;

        if (null != t) {
            state.taskState.failure = Utils.toServiceErrorResponse(t);
        }

        return state;
    }
}
