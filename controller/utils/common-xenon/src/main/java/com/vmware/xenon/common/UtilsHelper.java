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

package com.vmware.xenon.common;

/**
 * Temporary hack to make a protected method in Xenon accessible from our MicroServices.
 * <p>
 * The right solution is to expose Utils.getThreadContextId and Utils.setThreadContextId publicly, that are currently
 * marked as protected. Once that is done UtilsHelper will be removed.
 */
public final class UtilsHelper {
  public static String getThreadContextId() {
    return OperationContext.getContextId();
  }

  public static void setThreadContextId(String contextId) {
    OperationContext.setContextId(contextId);
  }
}
