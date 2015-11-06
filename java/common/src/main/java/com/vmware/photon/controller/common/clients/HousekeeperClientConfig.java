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

package com.vmware.photon.controller.common.clients;

/**
 * Configuration values for the housekeeper client.
 */
public class HousekeeperClientConfig {

  /**
   * Value to wait for image replication process.
   */
  private long imageReplicationTimeout;

  public HousekeeperClientConfig() {
  }

  /**
   * Getter for the image replication timeout. (Value is in MS)
   */
  public long getImageReplicationTimeout() {
    return this.imageReplicationTimeout;
  }

  /**
   * Setter for the image replication timeout. (Value is in MS)
   * @param timeoutMs
   */
  public void setImageReplicationTimeout(long timeoutMs) {
    this.imageReplicationTimeout = timeoutMs;
  }
}
