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

/*
 * Dropwizard
 * Copyright 2011-2012 Coda Hale and Yammer,Inc.
 * This product includes software developed by Coda Hale and Yammer,Inc.
 */
package com.vmware.photon.controller.common.logging;

import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;

/**
 * PrefixedExtendedThrowableProxyConverter.
 */
public class PrefixedExtendedThrowableProxyConverter extends PrefixedThrowableProxyConverter {
  @Override
  protected void extraData(StringBuilder builder, StackTraceElementProxy step) {
    if (step != null) {
      ThrowableProxyUtil.subjoinPackagingData(builder, step);
    }
  }
}
