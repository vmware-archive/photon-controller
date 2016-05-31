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
package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;

/**
 * Represents a logical group of Photon Controller Xenon services.
 */
public interface XenonServiceGroup {

    /**
     * Returns the name of the XenonServiceGroup.
     *
     * @return
     */
    String getName();

    /**
     * Starts the XenonServiceGroup.  This process usually includes the registration
     * of all of the Services / Factories with the XenonHost.
     *
     * @throws Throwable
     */
    void start() throws Throwable;

    /**
     * Checks the readiness state of the XenonServiceGroup.  This usually represents the
     * collective readiness state of all of the Services / Factories related to this
     * XenonServiceGroup.
     *
     * @return
     */
    boolean isReady();

    /**
     * This method is used by the PhotonControllerXenonHost to register itself with the
     * XenonServiceGroup.
     *
     * @param photonControllerXenonHost
     */
    void setPhotonControllerXenonHost(PhotonControllerXenonHost photonControllerXenonHost);
}
