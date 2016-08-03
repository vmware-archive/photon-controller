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

package com.vmware.photon.controller.api.frontend.utils;

import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;

/**
 * This interface defines utility functions related to network.
 */
public interface NetworkHelper {

  /**
   * Returns if the software defined network is enabled.
   */
  boolean isSdnEnabled();

  /**
   * Converts network information from that returned by agent to a format that VM creation can consume.
   */
  VmService.NetworkInfo convertAgentNetworkToVmNetwork(VmNetworkInfo agentNetwork) throws ExternalException;
}
