/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.certmanager.dcp;

import com.vmware.photon.controller.certmanager.Config;
import com.vmware.xenon.common.ServiceHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
