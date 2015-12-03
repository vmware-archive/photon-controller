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

package com.vmware.photon.controller.model.adapters.dockeradapter.client;

import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;

/**
 * Interface of docker client.
 */
public interface DockerClient {

  /**
   * Represents the execution result of runContainer function.
   */
  public static class RunContainerResponse {

    /**
     * The IP address of the container.
     */
    public String address;

    /**
     * The primary MAC address of the container.
     */
    public String primaryMAC;

    /**
     * The power state of the container.
     */
    public ComputeService.PowerState powerState;

    /**
     * The disk status of the container.
     */
    public DiskService.DiskStatus diskStatus;
  }

  /**
   * Sets the compute state instance for the client.
   * @param computeState Represents the compute state instance which contains container related information.
   * @return The docker client instance.
   */
  DockerClient withComputeState(ComputeService.ComputeState computeState);

  /**
   * Sets the disk state instance for the client.
   * @param diskState Represents the disk state instance which contains image related information.
   * @return The docker client instance.
   */
  DockerClient withDiskState(DiskService.Disk diskState);

  /**
   * Launches a container instance.
   * @return The result of the launching.
   */
  RunContainerResponse runContainer();

  /**
   * Deletes a container instance.
   */
  void deleteContainer();
}
