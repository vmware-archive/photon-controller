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
package com.vmware.photon.controller.clustermanager.rolloutplans;

import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.tasks.ClusterWaitTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.ClusterWaitTaskService;
import com.vmware.photon.controller.clustermanager.tasks.VmProvisionTaskFactoryService;
import com.vmware.photon.controller.clustermanager.tasks.VmProvisionTaskService;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateFactory;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.clustermanager.utils.ExceptionUtils;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.xenon.common.Service;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a basic rollout plan that provisions a node and then waits for the node to be ready using the
 * node's status-checker.
 */
public class BasicNodeRollout implements NodeRollout {

  public void run(final Service service,
                  final NodeRolloutInput input,
                  final FutureCallback<NodeRolloutResult> responseFutureCallback) {

    Preconditions.checkNotNull(service, "service cannot be null");
    Preconditions.checkNotNull(input, "input cannot be null");
    Preconditions.checkNotNull(responseFutureCallback, "responseFutureCallback cannot be null");
    input.validate();
    provisionNodes(service, input, responseFutureCallback);
  }

  private void provisionNodes(final Service service,
                              final NodeRolloutInput input,
                              final FutureCallback<NodeRolloutResult> responseFutureCallback) {

    final Queue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
    final Queue<String> nodeAddresses = new ConcurrentLinkedQueue<>();
    final AtomicInteger latch = new AtomicInteger(input.nodeCount);

    for (int i = 0; i < input.nodeCount; i++) {
      FutureCallback<VmProvisionTaskService.State> callback = new FutureCallback<VmProvisionTaskService.State>() {
        @Override
        public void onSuccess(@Nullable VmProvisionTaskService.State result) {
          switch (result.taskState.stage) {
            case FINISHED:
              nodeAddresses.add(result.vmIpAddress);
              break;
            case CANCELLED:
              exceptions.add(new IllegalStateException(String.format(
                  "VmProvisionTaskService was canceled. %s",
                  result.documentSelfLink)));
              break;
            case FAILED:
              exceptions.add(new IllegalStateException(String.format(
                  "VmProvisionTaskService failed with error %s. %s",
                  result.taskState.failure.message,
                  result.documentSelfLink)));
              break;
          }

          if (0 == latch.decrementAndGet()) {
            if (0 == exceptions.size()) {
              waitForNodes(service, input, new ArrayList<>(nodeAddresses), responseFutureCallback);
            } else {
              responseFutureCallback.onFailure(
                  ExceptionUtils.createMultiException(exceptions));
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          exceptions.add(t);
          if (0 == latch.decrementAndGet()) {
            responseFutureCallback.onFailure(
                ExceptionUtils.createMultiException(exceptions));
          }
        }
      };

      NodeTemplate template = NodeTemplateFactory.createInstance(input.nodeType);
      String scriptDirectory = HostUtils.getScriptsDirectory(service);

      Map<String, String> nodeProperties = new HashMap<>(input.nodeProperties);
      nodeProperties.put(NodeTemplateUtils.NODE_INDEX_PROPERTY, Integer.toString(i));
      nodeProperties.put(NodeTemplateUtils.HOST_ID_PROPERTY, UUID.randomUUID().toString());

      VmProvisionTaskService.State startState = new VmProvisionTaskService.State();
      startState.diskFlavorName = input.diskFlavorName;
      startState.imageId = input.imageId;
      startState.projectId = input.projectId;
      startState.vmFlavorName = input.vmFlavorName;
      startState.vmNetworkId = input.vmNetworkId;
      startState.vmTags = ClusterUtil.createClusterTags(input.clusterId, input.nodeType);
      startState.vmName = template.getVmName(nodeProperties);
      startState.userData = template.createUserDataTemplate(scriptDirectory, nodeProperties);
      startState.metaData = template.createMetaDataTemplate(scriptDirectory, nodeProperties);

      TaskUtils.startTaskAsync(
          service,
          VmProvisionTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          VmProvisionTaskService.State.class,
          ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
          callback);
    }
  }

  private void waitForNodes(final Service service,
                            final NodeRolloutInput input,
                            final List<String> nodeAddresses,
                            final FutureCallback<NodeRolloutResult> responseFutureCallback) {

    final Queue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
    final AtomicInteger latch = new AtomicInteger(input.nodeCount);

    for (int i = 0; i < input.nodeCount; i++) {
      FutureCallback<ClusterWaitTaskService.State> callback = new FutureCallback<ClusterWaitTaskService.State>() {
        @Override
        public void onSuccess(@Nullable ClusterWaitTaskService.State result) {
          switch (result.taskState.stage) {
            case FINISHED:
              break;
            case CANCELLED:
              exceptions.add(new IllegalStateException(String.format(
                  "ClusterWaitTaskService was canceled for cluster with ID %s. %s",
                  input.clusterId,
                  result.documentSelfLink)));
              break;
            case FAILED:
              exceptions.add(new IllegalStateException(String.format(
                  "ClusterWaitTaskService failed for cluster with ID %s with error %s. %s",
                  input.clusterId,
                  result.taskState.failure.message,
                  result.documentSelfLink)));
              break;
          }

          if (0 == latch.decrementAndGet()) {
            if (0 == exceptions.size()) {
              NodeRolloutResult rolloutResult = new NodeRolloutResult();
              rolloutResult.nodeAddresses = nodeAddresses;
              responseFutureCallback.onSuccess(rolloutResult);
            } else {
              responseFutureCallback.onFailure(
                  ExceptionUtils.createMultiException(exceptions));
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          exceptions.add(t);
          if (0 == latch.decrementAndGet()) {
            responseFutureCallback.onFailure(
                ExceptionUtils.createMultiException(exceptions));
          }
        }
      };

      ClusterWaitTaskService.State startState = new ClusterWaitTaskService.State();
      startState.nodeType = input.nodeType;
      startState.serverAddress = nodeAddresses.get(i);
      startState.clusterId = input.clusterId;

      TaskUtils.startTaskAsync(
          service,
          ClusterWaitTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          ClusterWaitTaskService.State.class,
          ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
          callback);
    }
  }
}
