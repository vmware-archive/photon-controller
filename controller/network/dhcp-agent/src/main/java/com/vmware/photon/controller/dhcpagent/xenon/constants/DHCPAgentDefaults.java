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

package com.vmware.photon.controller.dhcpagent.xenon.constants;

/**
 * Defines various DHCP agent constants.
 */
public class DHCPAgentDefaults {
    /**
     * The core pool size is minimum the number of threads which will be kept
     * alive by the thread pool executor service. Using a value of zero allows
     * the thread pool to die down to a state where no threads are present.
     * <p>
     * Since we are using a queue without a hard limit in order we need to set
     * the CORE_POOL_SIZE equal to the MAXIMUM_POOL_SIZE to ever get to use the
     * maximum.
     */
    public static final int CORE_POOL_SIZE = 16;

    /**
     * The maximum pool size is the maximum number of threads which the thread
     * pool executor service will spin up in response to new work items. After
     * this maximum is reached, new threads go onto the service's BlockingQueue
     * or are rejected per the service's rejection policy. Using a value of
     * sixteen is just a guess here, but some maximum pool size needs to be set
     * in order to prevent the JVM from running out of memory.
     */
    public static final int MAXIMUM_POOL_SIZE = 16;

    /**
     * The keep-alive time is the period after which the thread pool executor
     * service will kill off threads above the CORE_POOL_SIZE which have not
     * processed work items. A value of sixty seconds is taken from the default
     * value used for the CachedThreadPool executor.
     */
    public static final long KEEP_ALIVE_TIME = 60;
}
