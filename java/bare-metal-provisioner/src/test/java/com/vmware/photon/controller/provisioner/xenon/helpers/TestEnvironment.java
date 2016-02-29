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

import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.provisioner.xenon.ProvisionerXenonHost;

import org.apache.commons.io.FileUtils;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * TestMachine class hosting a Xenon host.
 */
public class TestEnvironment extends MultiHostEnvironment<ProvisionerXenonHost> {
  private TestEnvironment(int hostCount) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new ProvisionerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {

      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      hosts[i] = new ProvisionerXenonHost(BIND_ADDRESS, 0, sandbox);
    }
  }

  /**
   * Create instance of TestEnvironment with specified count of hosts and start all hosts.
   *
   * @return
   * @throws Throwable
   */
  public static TestEnvironment create(int hostCount) throws Throwable {
    TestEnvironment testEnvironment = new TestEnvironment(hostCount);
    testEnvironment.start();
    return testEnvironment;
  }

  public static void killUnixProcessesByName(String name) {
    List<Long> processIds = findUnixProcessIds("-e", name);
    for (Long pid : processIds) {
      try {
        Runtime.getRuntime().exec("kill " + pid);
      } catch (Throwable ex) {
        ;
      }
    }
  }

  public static List<Long> findUnixProcessIds(String filter, String name) {
    ArrayList processes = new ArrayList();

    try {
      String cmd = String.format("ps -o ppid,pid,ucomm %s", new Object[]{filter});
      Process p = Runtime.getRuntime().exec(cmd);
      BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
      input.readLine();

      while (true) {
        String[] columns;
        String ucomm;
        do {
          String line;

          if ((line = input.readLine()) == null) {
            input.close();
            return processes;
          }

          columns = line.trim().split("\\s+", 3);
          ucomm = columns[2].trim();
          if (name.startsWith(ucomm)){
            int i = 0;
          }
        } while(name != null && !ucomm.equalsIgnoreCase(name));

        try {
          Long pid = Long.valueOf(Long.parseLong(columns[1].trim()));
          processes.add(pid);
        } catch (Throwable ex11) {
          ;
        }
      }
    } catch (Throwable ex12) {
      return processes;
    }
  }
}
