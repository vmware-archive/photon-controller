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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

import java.util.concurrent.TimeUnit;

/**
 * Class implementing service to trigger synchronize IP leases for a subnet
 * from the cloud store to DHCP agent.
 */
public class SubnetIPLeaseSyncTriggerService extends StatelessService {

  public static final String SELF_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.HOUSEKEEPER_ROOT
      + "/subnet-ip-lease-sync-trigger";
  public static final int DEFAULT_PAGE_LIMIT = 1000;

  @Override
  public void handlePost(Operation patch) {
    try {
      ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
      SubnetIPLeaseSyncService.State state = patch.getBody(SubnetIPLeaseSyncService.State.class);
      processPost(state);
      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  /**
   * Does any additional processing after the start operation has been completed.
   *
   * @param state
   */
  private void processPost(SubnetIPLeaseSyncService.State state) {
    state.pageLimit = DEFAULT_PAGE_LIMIT;
    getHost().schedule(
            () -> {
              Operation.createPost(UriUtils.buildUri(getHost(), SubnetIPLeaseSyncService.FACTORY_LINK))
                      .setBody(state)
                      .sendWith(this);
            },
            Constants.SUBNET_IP_LEASE_SYNC_DELAY, TimeUnit.SECONDS);
  }
}
