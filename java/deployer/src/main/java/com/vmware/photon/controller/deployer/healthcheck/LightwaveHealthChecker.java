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

package com.vmware.photon.controller.deployer.healthcheck;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Health check for lightwave container service.
 */
public class LightwaveHealthChecker implements HealthChecker {

  private final int port;
  private final String ipAddress;

  private static final int CONNECTION_TIMEOUT = 1000;

  /**
   * Ctor.
   *
   * @param ipAddress
   * @param port
   */
  public LightwaveHealthChecker(String ipAddress, int port) {
    this.ipAddress = ipAddress;
    this.port = port;
  }

  /**
   * Checks if Lightwave container is up and running.
   *
   * @return
   */
  @Override
  public boolean isReady() {
    try {
      Socket s = new Socket();
      s.connect(new InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT);
      s.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
