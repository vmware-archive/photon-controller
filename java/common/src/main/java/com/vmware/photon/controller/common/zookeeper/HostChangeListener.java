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

package com.vmware.photon.controller.common.zookeeper;

import com.vmware.photon.controller.host.gen.HostConfig;

/**
 * Clients interested in getting notifications about host changes should implement
 * this interface.
 */
public interface HostChangeListener {
    /**
     * Callback that gets called when new host gets added.
     *
     * @param id         host id
     * @param hostConfig added host config
     */
    void onHostAdded(String id, HostConfig hostConfig);

    /**
     * Callback that gets called when a host is removed.
     *
     * @param id         host id
     * @param hostConfig removed host config
     */
    void onHostRemoved(String id, HostConfig hostConfig);

    /**
     * callback that gets called when a host is updated.
     *
     * @param id         host id
     * @param hostConfig
     */
    void onHostUpdated(String id, HostConfig hostConfig);

    /**
     * callback that gets called when a host is reported missing.
     *
     * @param id         host id
     */
    void hostMissing(String id);
}
