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
package com.vmware.photon.controller.certmanager.dcp;

import com.vmware.photon.controller.certmanager.Config;
import com.vmware.xenon.common.ServiceHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CertManager DCP host.
 */
public class CertManagerServiceHost extends ServiceHost {

  private static final Logger logger = LoggerFactory.getLogger(CertManagerServiceHost.class);

  public CertManagerServiceHost(
      @Config.Bind String bindAddress,
      @Config.Port int port
  ) throws Throwable {
    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port + 1;
    arguments.bindAddress = bindAddress;
    logger.info("Initializing DcpServer on port: {}", arguments.port);
    this.initialize(arguments);
  }

}
