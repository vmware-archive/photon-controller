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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.deployer.service.exceptions.ManagementVmAddressAlreadyInUseException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Callable;

/**
 * This class implements host management vm address validation as a Java callable
 * object.
 */
public class HostManagementVmAddressValidator implements Callable<Boolean> {

  private static final int HOST_PING_TIMEOUT = 5000;

  private final String address;

  public HostManagementVmAddressValidator(String address) {
    this.address = address;
  }

  /**
   * This function is the main function which tries to ping the address.
   *
   * @return Returns true in case of success. Throws exception if address is already in use.
   */
  public Boolean call() throws ManagementVmAddressAlreadyInUseException {
    try {
      InetAddress inetAddress = InetAddress.getByName(address);
      if (inetAddress.isReachable(HOST_PING_TIMEOUT)) {
        throw new ManagementVmAddressAlreadyInUseException(address);
      }
    } catch (IOException e) {
      // Empty
    }
    return true;
  }
}
