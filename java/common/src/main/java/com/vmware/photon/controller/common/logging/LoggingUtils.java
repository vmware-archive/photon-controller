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
 * Logging helper methods.
 *
 * These methods maintain or use properties in the underlying logging's
 * Mapped Diagnostic Context (MDC). This is thread-local storage.
 *
 * The upshot is that we stash the incoming user's request ID in the MDC,
 * and all of the logging messages can refer to it.
 *
 * There are a few important things to note:
 *
 * 1) This gets into logging messages via the log format strings (normally
 * found in the service's configuration file).
 *
 * 2) The thrift client (see ClientProxyImpl) gets the request ID from the
 * MDC so that it can pass it to thrift services (via tracing_info).
 *
 * 3) Xenon services do not use this mechanism normally. Instead they use the
 * Xenon context ID on operations to preserve the request ID. Xenon services
 * should use ServiceUtils for logging, to ensure that messages include
 * the request ID.
 */
public class LoggingUtils {
  public static final String REQUEST_ID_KEY = "requestId";
  public static final String REQUEST_KEY = "request";

  /**
   * Get the request ID from the logging MDC.
   */
  public static String getRequestId() {
    return MDC.get(REQUEST_ID_KEY);
  }

  /**
   * Set the request ID in the logging MDC. Normally you should not
   * need to do this.
   */
  public static void setRequestId(String requestId) {
    MDC.put(REQUEST_KEY, formatRequestIdLogSection(requestId));
    MDC.put(REQUEST_ID_KEY, requestId);
  }

  /**
   * Create a string that can be used as a prefix in a log message that has
   * the request ID embedded. This is used by the Xenon logging method in
   * ServiceUtils. It does not use the MDC: the request ID must be passed
   * as an argument.
   */
  public static String formatRequestIdLogSection(String requestId) {
    return String.format(" [Req: %s]", requestId);
  }

  /**
   * Clear the request ID. Normally you do not need to call this.
   */
  public static void clearRequestId() {
    MDC.remove(REQUEST_KEY);
    MDC.remove(REQUEST_ID_KEY);
  }
}
