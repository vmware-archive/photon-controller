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

import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Implements Health check for Xenon based components such as CloudStore.
 */
public class XenonBasedHealthChecker implements HealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(XenonBasedHealthChecker.class);

  private final Service service;
  private final String address;
  private final int port;

  @Inject
  public XenonBasedHealthChecker(@Assisted Service service, @Assisted String address, @Assisted int port) {
    this.service = service;
    this.address = address;
    this.port = port;
  }

  @Override
  public boolean isReady() {
    try {
      URI uri = UriUtils.buildUri("http", address, port, ServiceUriPaths.STATUS_SERVICE, null);
      Operation getOperation = Operation
          .createGet(uri)
          .setUri(uri)
          .forceRemote();
      Operation completedOperation = ServiceUtils.doServiceOperation(service, getOperation);
      Status status = completedOperation.getBody(Status.class);
      logger.info("Xenon service returned status [{}:{}]: {}", address, port, status.getType());
      return status.getType() == StatusType.READY;
    } catch (Throwable e) {
      logger.warn("GET to Xenon status service failed [{}:{}]: {}", address, port, e);
      return false;
    }
  }
}
