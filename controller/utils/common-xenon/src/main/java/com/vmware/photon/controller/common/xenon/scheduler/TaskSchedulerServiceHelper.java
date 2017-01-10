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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Helper for TaskSchedulerService.
 */
public class TaskSchedulerServiceHelper {

  public static final long OWNER_SELECTION_TIMEOUT = TimeUnit.SECONDS.toMicros(5);

  /**
   * Send a patch to the owner of the TaskSchedulerService.
   *
   * @param service
   * @param host
   * @param taskSchedulerServiceSelfLink
   * @param state
   * @param referLink
   */
  public static void sendPatchToOwner(Service service, ServiceHost host, String taskSchedulerServiceSelfLink,
                                      TaskSchedulerService.State state, URI referLink) {

    Operation.CompletionHandler handler =
        (Operation op, Throwable failure) -> {
          if (null != failure) {
            ServiceUtils.logSevere(service, failure);
            return;
          }

          NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
          Operation patch = Operation
              .createPatch(UriUtils.buildUri(rsp.ownerNodeGroupReference, taskSchedulerServiceSelfLink))
              .setBody(state)
              .setReferer(referLink);

          host.sendRequest(patch);
        };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(OWNER_SELECTION_TIMEOUT))
        .setCompletion(handler);
    host.selectOwner(null, taskSchedulerServiceSelfLink, selectOwnerOp);
  }
}
