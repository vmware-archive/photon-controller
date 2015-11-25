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

package com.vmware.photon.controller.common.dcp;

import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Class implements utility methods around Operation objects.
 */
public class OperationUtils {

  private static final Logger logger = LoggerFactory.getLogger(OperationUtils.class);

  /**
   * Returns true if operation still needs to be completed.
   *
   * @param op
   * @return
   */
  public static boolean isCompleted(Operation op) {
    return (null == op || null == op.getCompletion());
  }

  public static URI getLocalHostUri() {
    URI uri;
    String host;
    InetAddress localHostInetAddress = getLocalHostInetAddress();

    if (localHostInetAddress != null) {
      host = localHostInetAddress.getHostAddress();
    } else {
      host = "http://unknownhost";
    }

    try {
      uri = new URI(host);
    } catch (URISyntaxException e) {
      logger.warn("Exception retrieving local host address for use as referer", e);
      throw new RuntimeException(e);
    }

    return uri;
  }

  public static InetAddress getLocalHostInetAddress() {
    try {
      return InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      logger.warn("Exception retrieving local host address", e);
    }
    return null;
  }

  public static Operation handleCompletedOperation(Operation requestedOperation, Operation completedOperation)
      throws TimeoutException, DocumentNotFoundException, BadRequestException {
    switch (completedOperation.getStatusCode()) {
      case Operation.STATUS_CODE_OK:
      case Operation.STATUS_CODE_ACCEPTED:
        return completedOperation;
      case Operation.STATUS_CODE_NOT_FOUND:
        throw new DocumentNotFoundException(requestedOperation, completedOperation);
      case Operation.STATUS_CODE_TIMEOUT:
        throw new TimeoutException(completedOperation.getBody(ServiceErrorResponse.class).message);
      case Operation.STATUS_CODE_BAD_REQUEST:
        throw new BadRequestException(requestedOperation, completedOperation);
      default:
        throw new DcpRuntimeException(requestedOperation, completedOperation);
    }
  }
}
