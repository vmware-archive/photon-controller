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

package com.vmware.photon.controller.api.frontend.clients.status;

import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonException;
import com.vmware.photon.controller.common.xenon.host.StatusService;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

/**
 * Implementation via Xenon REST call to get status.
 */
public class XenonStatusProvider implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(XenonStatusProvider.class);
  private final XenonRestClient xenonRestClient;

  public XenonStatusProvider(XenonRestClient xenonRestClient) {
    this.xenonRestClient = xenonRestClient;
  }

  @Override
  public Status getStatus() {
    try {
      xenonRestClient.start();
      Operation operation = xenonRestClient.get(StatusService.SELF_LINK);
      return operation.getBody(Status.class);
    } catch (DocumentNotFoundException | TimeoutException ex) {
      logger.error("Xenon REST call unreachable", ex);
      Status status = new Status(StatusType.UNREACHABLE);
      status.setMessage(ex.getMessage());
      return status;
    } catch (XenonException | Exception ex) {
      logger.error("Xenon REST call error", ex);
      Status status = new Status(StatusType.ERROR);
      status.setMessage(ex.getMessage());
      return status;
    } finally {
      xenonRestClient.stop();
    }
  }
}
