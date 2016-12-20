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

import com.vmware.xenon.common.Service;

import com.google.common.util.concurrent.FutureCallback;

/**
 * Represents an interface rolling out nodes of a specific type.
 */
public interface NodeRollout {

  /**
   * Performs the Rollout operation.
   *
   * @param service  the service requesting for the rollout operation
   * @param input    the input for the rollout operation
   * @param callback Callback that is invoked after a rollout completes.
   */
  void run(final Service service,
           final NodeRolloutInput input,
           final FutureCallback<NodeRolloutResult> callback);
}
