/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

/**
 * This class holds the structure for release IP task that is referred by release IP service
 * to complete the operation. It also includes the result of the request on a successful
 * IP release or an error on a failure.
 */
public class ReleaseIPTask extends ServiceDocument {

    /**
     * The state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the ability control of executing operations or stage transitions of ReleaseIPService.
     */
    @DefaultInteger(0)
    @Immutable
    public Integer controlFlags;

    /**
     * The IP address that needs to be released.
     */
    public String ipAddress;

    /**
     * The MAC address for which IP needs to be released.
     */
    public String macAddress;

    /**
     * The network interface name to which IP and MAC addresses belong to.
     */
    public String networkInterface;

    /**
     * The unique id of the request.
     */
    public String requestId;

    /**
     * The result of the release IP request.
     */
    public DHCPDriver.Response response;
}
