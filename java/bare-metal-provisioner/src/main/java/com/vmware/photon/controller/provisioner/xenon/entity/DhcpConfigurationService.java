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

package com.vmware.photon.controller.provisioner.xenon.entity;

import com.vmware.photon.controller.provisioner.xenon.helpers.DhcpUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.net.URI;
import java.util.Map;

/**
 * This class implements a Xenon micro-service which provides a plain data object
 * representing dhcp configuration.
 */
public class DhcpConfigurationService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link DhcpConfigurationService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * enable subnet/lease * .
     */
    public boolean isEnabled;

    /**
     * Reference image to boot.
     */
    public URI hostBootImageReference;

    /**
     * Duration of lease.
     */
    public long leaseDurationTimeMicros;

    /**
     * IPv4 addresses of routers * .
     */
    public String[] routerAddresses;

    /**
     * IPv4 addresses of nameservers * .
     */
    public String[] nameServerAddresses;

    /**
     * User modifiable map passed to image templater.
     */
    public Map<String, String> data;
  }

  public DhcpConfigurationService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      State config = start
          .getBody(State.class);
      DhcpUtils.validate(config);
      start.complete();
    } catch (IllegalArgumentException e) {
      start.fail(e);
      return;
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    State curr = getState(patch);
    State patchBody = patch.getBody(State.class);

    curr.isEnabled = patchBody.isEnabled;
    try {
      DhcpUtils.validate(patchBody);
      if (patchBody.hostBootImageReference != null) {
        curr.hostBootImageReference = patchBody.hostBootImageReference;
      }

      if (patchBody.routerAddresses != null && patchBody.routerAddresses.length != 0) {
        curr.routerAddresses = patchBody.routerAddresses;
      }

      if (patchBody.nameServerAddresses != null && patchBody.nameServerAddresses.length != 0) {
        curr.nameServerAddresses = patchBody.nameServerAddresses;
      }
      if (patchBody.data != null) {
        curr.data = patchBody.data;
      }
    } catch (Throwable e) {
      patch.fail(e);
      return;
    }
    patch.setBody(curr).complete();
  }
}
