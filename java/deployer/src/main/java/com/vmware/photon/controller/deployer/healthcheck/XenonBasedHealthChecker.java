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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements Health check for Xenon based components such as CloudStore and Scheduler.
 */
public class XenonBasedHealthChecker implements HealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(XenonBasedHealthChecker.class);

  private final Service service;
  private final String address;
  private final List<Integer> ports;

  public XenonBasedHealthChecker(Service service, String address, int port) {
    this.service = service;
    this.address = address;
    this.ports = new ArrayList<>();
    this.ports.add(port);
  }

  public XenonBasedHealthChecker(Service service, String address, List<Integer> ports) {
    this.service = service;
    this.address = address;
    this.ports = ports;
  }

  @Override
  public boolean isReady() {
    try {
      // Previously there was a one to one correlation between container and service but now
      // that it is no longer true, multiple ports may contribute to the overall health of a
      // container, thus the introduction of a list of ports.
      for (Integer port : this.ports){
        URI uri = UriUtils.buildUri("http", address, port, ServiceUriPaths.STATUS_SERVICE, null);
        Operation getOperation = Operation
                .createGet(uri)
                .setUri(uri)
                .forceRemote();
        Operation completedOperation = ServiceUtils.doServiceOperation(service, getOperation);
        Status status = completedOperation.getBody(Status.class);
        logger.info("Xenon service returned status [{}:{}]: {}", address, port, status.getType());

        // As soon as we find any service not ready we might as well return and report not
        // ready.  If all return ready we will finish the for loop and return true
        if (status.getType() != StatusType.READY) {
          return false;
        }
      }

      return true;
    } catch (Throwable e) {
      logger.error("GET to Xenon service failed [{}:{}]: {}", address, ports, e);
      return false;
    }
  }
}
