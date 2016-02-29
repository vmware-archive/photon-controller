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
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon micro-service which provides a plain data object
 * representing dhcp lease.
 */
public class DhcpLeaseService extends StatefulService {
  public static final long EXPIRE_DELTA_US = TimeUnit.SECONDS.toMicros(30);

  /**
   * This class defines the document state associated with a single
   * {@link DhcpLeaseService} instance.
   */
  public static class DhcpLeaseState extends ServiceDocument {
    public static final String FIELD_NAME_NETWORK_DESCRIPTION_LINK = "networkDescriptionLink";

    /**
     * Links to the subnet the lease belongs to.
     */
    public String networkDescriptionLink;

    /**
     * MAC address of host.
     */
    public String mac;

    /**
     * IPv4 address to assign to host.
     */
    public String ip;

    public void copyTo(DhcpLeaseState target) {
      super.copyTo(target);
      target.networkDescriptionLink = this.networkDescriptionLink;
      target.mac = this.mac;
      target.ip = this.ip;
    }

    static String buildSelfLink(DhcpSubnetService.DhcpSubnetState subnetState, String mac) {
      return String.format("%s-%s", subnetState.id, DhcpUtils.normalizeMac(mac));
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link DhcpLeaseService} instance.
   */
  public static class DhcpLeaseStateWithDescription extends DhcpLeaseState {
    /**
     * The network description this lease belongs to. This field is filled in by the run-time
     * via the networkDescriptionLink.
     */
    public DhcpSubnetService.DhcpSubnetState description;

    public static DhcpLeaseStateWithDescription create(DhcpLeaseState state) {
      DhcpLeaseStateWithDescription withDescription = new DhcpLeaseStateWithDescription();
      state.copyTo(withDescription);
      return withDescription;
    }
  }

  public DhcpLeaseService() {
    super(DhcpLeaseState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleGet(Operation get) {
    DhcpLeaseState state = getState(get);

    // Retrieve the description and include in an augmented version of our state
    Operation getDesc = Operation
        .createGet(this, state.networkDescriptionLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                get.fail(e);
                return;
              }

              DhcpSubnetService.DhcpSubnetState dhcpSubnetState = o.getBody(DhcpSubnetService.DhcpSubnetState.class);
              DhcpLeaseStateWithDescription withDescription = DhcpLeaseStateWithDescription
                  .create(state);
              withDescription.description = dhcpSubnetState;
              get.setBody(withDescription).complete();
            });
    sendRequest(getDesc);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      throw new IllegalArgumentException("body is required");
    }

    DhcpLeaseState state = start.getBody(DhcpLeaseState.class);

    scheduleTaskExpiration(state.documentExpirationTimeMicros);
    start.complete();
  }

  /**
   * Patch the lease's expiry time.
   */
  @Override
  public void handlePatch(Operation patch) {
    DhcpLeaseState currentBody = getState(patch);
    DhcpLeaseState patchBody = patch.getBody(DhcpLeaseState.class);

    if (patchBody.documentExpirationTimeMicros <= 0) {
      patch.fail(new Throwable("cannot patch lease to not expire"));
      return;
    }

    currentBody.documentExpirationTimeMicros = patchBody.documentExpirationTimeMicros;
    patch.setBody(currentBody).complete();
    scheduleTaskExpiration(currentBody.documentExpirationTimeMicros);
  }

  private void releaseLease() {
    Operation getOp = Operation
        .createGet(this.getUri())
        .setCompletion((o, e) -> {
          if (e != null) {
            logWarning(e.toString());
            return;
          }
          DhcpLeaseState lease = o.getBody(DhcpLeaseState.class);

          // minus 1 due to rounding of micros to millis when creating the task.
          boolean renewed = lease.documentExpirationTimeMicros - EXPIRE_DELTA_US
              - TimeUnit.MICROSECONDS.toSeconds(1) > Utils.getNowMicrosUtc();

          if ((lease.documentExpirationTimeMicros == 0) || renewed) {
            // Something changed.  Likely the lease was renewed.
            return;
          }

          DhcpSubnetService.ReleaseLeaseRequest req = new DhcpSubnetService.ReleaseLeaseRequest();
          req.kind = DhcpSubnetService.ReleaseLeaseRequest.KIND;
          req.mac = lease.mac;
          req.ip = lease.ip;

          sendRequest(Operation
              .createPatch(
                  UriUtils.buildUri(this.getHost(),
                      lease.networkDescriptionLink))
              .setBody(req)
              .setCompletion(
                  (op, ep) -> {
                    if (ep != null) {
                      logWarning("Lease release failed w/ "
                          + ep.toString());
                      return;
                    }

                    logFine("IP %s released from %s", req.ip,
                        lease.networkDescriptionLink);
                  }));
        });

    sendRequest(getOp);
  }

  /**
   * Schedule a task to release the lease from the subnet before the lease expires.  Lease expiration happens
   * relative to the maintenance task.
   *
   * @param documentExpirationTimeMicros the time the lease will expire
   */
  private void scheduleTaskExpiration(long documentExpirationTimeMicros) {

    long delta = (documentExpirationTimeMicros - EXPIRE_DELTA_US - Utils.getNowMicrosUtc()) / 1000;
    delta = Math.max(1, delta);
    getHost().schedule(() -> {
      releaseLease();
    }, delta, TimeUnit.MILLISECONDS);
  }
}
