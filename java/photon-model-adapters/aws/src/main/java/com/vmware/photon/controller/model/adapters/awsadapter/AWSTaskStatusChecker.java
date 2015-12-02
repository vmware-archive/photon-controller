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

package com.vmware.photon.controller.model.adapters.awsadapter;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.xenon.common.StatelessService;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Class to check if an instance is in the desired state.
 * If the vm is in the desired state invoke the consumer,
 * else reschedule to check in 5 seconds
 */
public class AWSTaskStatusChecker {

  private String instanceId;
  private AmazonEC2AsyncClient amazonEC2Client;
  private String desiredState;
  private Consumer<Instance> consumer;
  private ComputeInstanceRequest computeRequest;
  private StatelessService service;

  private AWSTaskStatusChecker(AmazonEC2AsyncClient amazonEC2Client, String instanceId,
                               String desiredState,
                               Consumer<Instance> consumer, ComputeInstanceRequest computeRequest,
                               StatelessService service) {
    this.instanceId = instanceId;
    this.amazonEC2Client = amazonEC2Client;
    this.consumer = consumer;
    this.desiredState = desiredState;
    this.computeRequest = computeRequest;
    this.service = service;
  }

  public static AWSTaskStatusChecker create(AmazonEC2AsyncClient amazonEC2Client,
                                            String instanceId,
                                            String desiredState,
                                            Consumer<Instance> consumer, ComputeInstanceRequest computeRequest,
                                            StatelessService service) {
    return new AWSTaskStatusChecker(amazonEC2Client, instanceId, desiredState, consumer,
        computeRequest, service);
  }

  public void start() {
    DescribeInstancesRequest descRequest = new DescribeInstancesRequest();
    List<String> instanceIdList = new ArrayList<String>();
    instanceIdList.add(instanceId);
    descRequest.setInstanceIds(instanceIdList);
    AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> describeHandler =
        new AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult>() {

          @Override
          public void onError(Exception exception) {
            AWSUtils.sendFailurePatchToTask(service,
                computeRequest.provisioningTaskReference,
                exception);
            return;
          }

          @Override
          public void onSuccess(
              DescribeInstancesRequest request,
              DescribeInstancesResult result) {
            Instance instance = result.getReservations().get(0)
                .getInstances().get(0);
            if (!instance.getState().getName()
                .equals(desiredState)) {
              // if the task is not in the running state, schedule thread to run again in 5 seconds
              service.getHost().schedule(() -> {
                AWSTaskStatusChecker.create(amazonEC2Client, instanceId,
                    desiredState, consumer, computeRequest, service).start();
              }, 5, TimeUnit.SECONDS);
              return;
            }
            consumer.accept(instance);
            return;
          }
        };
    amazonEC2Client.describeInstancesAsync(descRequest, describeHandler);
  }
}
