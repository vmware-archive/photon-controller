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

package com.vmware.photon.controller.deployer.dcp.util;

import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.xenon.common.Service;

import java.net.InetAddress;

/**
 * This class implements helper routines for calls to the ESX Cloud REST API.
 */

public class IPUtils {
  public static String getNumericStringFromIpAddress(Service service, String ipAddress) {
    long myId = 0;
    try {
      InetAddress ip = InetAddress.getByName(ipAddress);
      for (byte b: ip.getAddress()) {
        myId = myId << 8 | (b & 0xFF);
      }
    } catch (Exception e) {
      String[] parts = ipAddress.split("\\.");
      for (int i = 0; i < parts.length; i++) {
        int power = 3 - i;
        myId += (Integer.parseInt(parts[i]) % 256 * Math.pow(256, power));
      }
    }

    ServiceUtils.logInfo(service, "MyId is " + myId);
    return String.valueOf(myId);
  }
}
