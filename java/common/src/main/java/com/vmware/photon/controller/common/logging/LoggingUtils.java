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

package com.vmware.photon.controller.common.logging;

import org.slf4j.MDC;

/**
 * Logging helper functions.
 */
public class LoggingUtils {
  protected static final String REQUEST_ID_KEY = "requestId";
  protected static final String REQUEST_KEY = "request";

  public static String getRequestId() {
    return MDC.get(REQUEST_ID_KEY);
  }

  public static void setRequestId(String requestId) {
    MDC.put(REQUEST_KEY, formatRequestIdLogSection(requestId));
    MDC.put(REQUEST_ID_KEY, requestId);
  }

  public static String formatRequestIdLogSection(String requestId) {
    return String.format(" [Req: %s]", requestId);
  }
}
