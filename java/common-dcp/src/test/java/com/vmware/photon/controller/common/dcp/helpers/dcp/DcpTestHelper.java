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

package com.vmware.photon.controller.common.dcp.helpers.dcp;

import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceDocumentDescription;

import java.util.EnumSet;

/**
 * This class implements helper routines for DCP unit tests.
 */
public class DcpTestHelper {
  /**
   * Compare the hash of two documents.
   *
   * @param type
   * @param document1
   * @param document2
   * @return the value {@code 0} if hash of two documents are equal;
   * a value less than {@code 0} if hash of document1
   * is lexicographically less than hash of document2; and a
   * value greater than {@code 0} otherwise.
   */
  public static <T extends ServiceDocument> boolean equals(
      Class<T> type, T document1, T document2) throws IllegalAccessException {

    ServiceDocumentDescription documentDescription = ServiceDocumentDescription.Builder.create()
        .buildDescription(type, EnumSet.noneOf(Service.ServiceOption.class));

    return ServiceDocument.equals(documentDescription, document1, document2);
  }

  /**
   * This is a temporary helper method.
   * Once we have a new DCP host for the cloud-store services
   * and a mechanism to inject its ServerSet we will not need this.
   * DcpRestClient currently assumes the server set given to it is a
   * thrift server set and converts the port to dcp port for its operations.
   *
   * @param dcpPort
   * @return
   */
  public static int convertDcpPortToThriftPort(int dcpPort) {
    return dcpPort - 1;
  }
}
