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

package com.vmware.photon.controller.provisioner.xenon.helpers;

import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationService;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class for DHCP related entitities.
 */
public class DhcpUtils {
  public static void validate(DhcpConfigurationService.State configuration)
      throws IllegalArgumentException {
    // Check the configuration
    if (configuration != null) {
      if (configuration.routerAddresses != null) {
        for (String o : configuration.routerAddresses) {
          if (o != null) {
            isValidInetAddress(o);
          }
        }
      }

      if (configuration.nameServerAddresses != null) {
        for (String o : configuration.nameServerAddresses) {
          if (o != null) {
            isValidInetAddress(o);
          }
        }
      }
    }
  }

  public static void isValidInetAddress(String ip) throws IllegalArgumentException {
    if (ip != null && !ip.isEmpty()) {
      if (!ip.contains(":")) {
        String[] segments = ip.split("\\.");
        if (segments.length != 4) {
          throw new IllegalArgumentException("IP does not appear valid:" + ip);
        }

        try {
          InetAddress.getByName(ip);
        } catch (UnknownHostException var3) {
          throw new IllegalArgumentException(var3);
        }
      }

    } else {
      throw new IllegalArgumentException("IP is missing or empty");
    }
  }

  public static void isRFC1918(String subnetAddress) throws IllegalArgumentException {
    String address = null;
    if (subnetAddress != null && !subnetAddress.isEmpty()) {
      if (subnetAddress.contains("/")) {
        String[] t = subnetAddress.split("/");
        address = t[0];
      }

      isValidInetAddress(address);

      InetAddress ipAddress;
      try {
        ipAddress = InetAddress.getByName(address);
      } catch (Throwable var4) {
        throw new IllegalArgumentException(var4.getMessage());
      }

      if (!ipAddress.isSiteLocalAddress()) {
        throw new IllegalArgumentException("must be an RFC-1918 address or CIDR");
      }
    } else {
      throw new IllegalArgumentException("IP or subnet is missing or empty");
    }
  }

  public static String normalizeMac(String mac) throws IllegalArgumentException {
    mac = mac.replaceAll("[:-]", "");
    mac = mac.toLowerCase();
    return mac;
  }

  public static void isCIDR(String network) throws IllegalArgumentException {
    String[] hostMask = network.split("/");
    if (hostMask.length != 2) {
      throw new IllegalArgumentException("subnetAddress is not a CIDR");
    } else {
      isValidInetAddress(hostMask[0]);
      if (Integer.parseUnsignedInt(hostMask[1]) > 32) {
        throw new IllegalArgumentException("CIDR mask may not be larger than 32");
      }
    }
  }

}
