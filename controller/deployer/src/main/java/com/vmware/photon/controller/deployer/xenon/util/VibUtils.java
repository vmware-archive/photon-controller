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

package com.vmware.photon.controller.deployer.xenon.util;

import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.xenon.common.Service;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * This class contains utilities to upload/install/remove vibs.
 */
public class VibUtils {

  /**
   * This string specifies the script which is used to install a VIB.
   */
  public static final String DELETE_VIB_SCRIPT_NAME = "esx-delete-agent2";

  public static Runnable removeVibs(
      HostService.State hostState,
      Service service,
      Consumer<Throwable> completion) {

    return new Runnable() {

      @Override
      public void run() {
        try {
          DeployerContext deployerContext = HostUtils.getDeployerContext(service);
          for (String vibName : deployerContext.getVibUninstallOrder()) {
            ServiceUtils.logInfo(service, "Uninstalling vib [%s] from host [%s]", vibName, hostState.hostAddress);
            List<String> command = Arrays.asList(
                "./" + DELETE_VIB_SCRIPT_NAME,
                hostState.hostAddress,
                hostState.userName,
                hostState.password,
                vibName);

            File scriptLogFile = new File(deployerContext.getScriptLogDirectory(), DELETE_VIB_SCRIPT_NAME + "-" +
                hostState.hostAddress + "-" + UUID.randomUUID().toString() + ".log");

            ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
                .directory(deployerContext.getScriptDirectory())
                .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
                .redirectErrorStream(true)
                .build();

            int scriptReturnCode = scriptRunner.call();

            if (scriptReturnCode != 0) {
              ServiceUtils.logSevere(service, DELETE_VIB_SCRIPT_NAME + " returned " + scriptReturnCode);
              ServiceUtils.logSevere(service, "Script output: " + FileUtils.readFileToString(scriptLogFile));
              throw new IllegalStateException(
                    "Installing VIB file "
                  + vibName
                  + " to host "
                  + hostState.hostAddress
                  + " failed with exit code "
                  + scriptReturnCode);
            }
          }
        } catch (Throwable t) {
          completion.accept(t);
        }
        completion.accept(null);
      }

    };
  }

}
