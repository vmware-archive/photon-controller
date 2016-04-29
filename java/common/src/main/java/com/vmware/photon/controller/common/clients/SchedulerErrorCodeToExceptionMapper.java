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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughCpuResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughDatastoreCapacityException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughMemoryResourceException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;

/**
 * A Mapping from a failed PlaceResultCode to the corresponding RpcException of the operation failure.
 */
public class SchedulerErrorCodeToExceptionMapper {

  /**
   * This method examines a PlaceResultCode that is not OK and creates an exception with the provided error.
   * @param resultCode the failed result code
   * @param error
   * @throws RpcException
   */
  public static void mapErrorCodeToException(PlaceResultCode resultCode, String error) throws RpcException {
    // The PlaceResultCode does not include PlaceResultCode.OK since the agent returns a PlaceResponse
    // and the scheduler returns a PlacementTask.
    switch (resultCode) {
      case NO_SUCH_RESOURCE:
        throw new NoSuchResourceException(error);
      case NOT_ENOUGH_CPU_RESOURCE:
        throw new NotEnoughCpuResourceException(error);
      case NOT_ENOUGH_MEMORY_RESOURCE:
        throw new NotEnoughMemoryResourceException(error);
      case NOT_ENOUGH_DATASTORE_CAPACITY:
        throw new NotEnoughDatastoreCapacityException(error);
      case RESOURCE_CONSTRAINT:
        throw new ResourceConstraintException(error);
      case SYSTEM_ERROR:
        throw new SystemErrorException(error);
      case INVALID_STATE:
        throw new InvalidAgentStateException(error);
      default:
        throw new RpcException(String.format("Unknown result: %s : %s", resultCode, error));
    }
  }
}
