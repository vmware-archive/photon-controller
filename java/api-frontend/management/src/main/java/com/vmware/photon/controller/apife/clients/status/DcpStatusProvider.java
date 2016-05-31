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

package com.vmware.photon.controller.apife.clients.status;

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
 * Implementation via DCP REST call to get status.
 */
public class DcpStatusProvider implements StatusProvider {

  private static final Logger logger = LoggerFactory.getLogger(DcpStatusProvider.class);
  private final XenonRestClient dcpRestClient;

  public DcpStatusProvider(XenonRestClient dcpRestClient) {
    this.dcpRestClient = dcpRestClient;
  }

  @Override
  public Status getStatus() {
    try {
      dcpRestClient.start();
      Operation operation = dcpRestClient.get(StatusService.SELF_LINK);
      return operation.getBody(Status.class);
    } catch (DocumentNotFoundException | TimeoutException ex) {
      logger.error("DCP REST call unreachable", ex);
      Status status = new Status(StatusType.UNREACHABLE);
      status.setMessage(ex.getMessage());
      return status;
    } catch (XenonException | Exception ex) {
      logger.error("DCP REST call error", ex);
      Status status = new Status(StatusType.ERROR);
      status.setMessage(ex.getMessage());
      return status;
    } finally {
      dcpRestClient.stop();
    }
  }
}
