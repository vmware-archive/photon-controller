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

package com.vmware.photon.controller.common.xenon.host;

import com.vmware.xenon.common.ServiceHost;

import java.nio.file.Paths;

/**
 * This class implements a base class for {@link ServiceHost} classes in Photon Controller.
 */
public abstract class AbstractServiceHost extends ServiceHost {

  public AbstractServiceHost(XenonConfig xenonConfig) throws Throwable {
    Arguments arguments = new Arguments();

    arguments.bindAddress = xenonConfig.getBindAddress();
    arguments.sandbox = Paths.get(xenonConfig.getStoragePath());
    arguments.peerNodes = xenonConfig.getPeerNodes();
    // Check if securePort is used, this means authentication is enabled.
    if (xenonConfig.getSecurePort() != null) {
      // authentication enabled
      arguments.securePort = xenonConfig.getSecurePort();
      arguments.keyFile = Paths.get(xenonConfig.getKeyFile());
      arguments.certificateFile = Paths.get(xenonConfig.getCertificateFile());
      arguments.sslClientAuthMode = ServiceHostState.SslClientAuthMode.NEED;
      arguments.port = -1;
    } else {
      arguments.port = xenonConfig.getPort();
    }

    this.initialize(arguments);
  }
}
