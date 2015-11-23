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

package com.vmware.photon.controller.model;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility functions used in provisioning hosts.
 */
public class ProvisioningUtils {

  /**
   * Verify if IP string is an IPv4 address.
   *
   * @param ip ip to verify
   * @throws IllegalArgumentException
   */
  public static void isValidInetAddress(String ip) throws IllegalArgumentException {

    // Opened issue #84 to track proper validation
    if (ip == null || ip.isEmpty()) {
      throw new IllegalArgumentException("IP is missing or empty");
    }

    if (ip.contains(":")) {
      // implement IPv6 validation
    } else {
      String[] segments = ip.split("\\.");
      if (segments.length != 4) {
        throw new IllegalArgumentException("IP does not appear valid:" + ip);
      }
      // it appears to be literal IP, its safe to use the getByName method
      try {
        InetAddress.getByName(ip);
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }
}
