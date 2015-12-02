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

import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import java.net.URI;


/**
 * Utility methods for AWS Adapter.
 */
public class AWSUtils {

  public static void sendFailurePatchToTask(StatelessService service, URI taskLink, Throwable t) {
    service.logWarning(Utils.toString(t));
    sendPatchToTask(service, taskLink, t);
  }

  public static void sendPatchToTask(StatelessService service, URI taskLink) {
    sendPatchToTask(service, taskLink, null);
  }

  private static void sendPatchToTask(StatelessService service, URI taskLink, Throwable t) {
    ProvisionComputeTaskState provisioningTaskBody = new ProvisionComputeTaskState();
    TaskState taskInfo = new TaskState();
    if (t == null) {
      taskInfo.stage = TaskState.TaskStage.FINISHED;
    } else {
      taskInfo.failure = Utils.toServiceErrorResponse(t);
      taskInfo.stage = TaskState.TaskStage.FAILED;
    }
    provisioningTaskBody.taskInfo = taskInfo;
    service.sendRequest(Operation
       .createPatch(taskLink)
       .setBody(provisioningTaskBody));
  }

  public static AmazonEC2AsyncClient getAsyncClient(AuthCredentialsServiceState credentials,
                                                    String region,
                                                    boolean isMockRequest) {
    AmazonEC2AsyncClient client = new AmazonEC2AsyncClient(new BasicAWSCredentials(
        credentials.privateKeyId,
        credentials.privateKey));

    client.setRegion(Region.getRegion(Regions
        .fromName(region)));

    // make a call to validate credentials
    if (!isMockRequest) {
      client.describeAvailabilityZones();
    }
    return client;
  }
}
