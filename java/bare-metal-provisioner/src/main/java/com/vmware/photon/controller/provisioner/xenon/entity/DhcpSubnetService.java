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

import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.provisioner.xenon.helpers.DhcpUtils;
import com.vmware.photon.controller.provisioner.xenon.helpers.IPRange;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon micro-service which provides a plain data object
 * representing dhcp subnet.
 */
public class DhcpSubnetService extends StatefulService {

  public static final long DEFAULT_LEASE_EXPIRATION_IN_MICROS = TimeUnit.MINUTES.toMicros(60);

  /**
   * Lease request.
   */
  public static class LeaseRequest {
    public String kind;
  }

  /**
   * Acquire lease request.
   */
  public static class AcquireLeaseRequest extends LeaseRequest {
    public static final String KIND = Utils.buildKind(AcquireLeaseRequest.class);
    public long expirationTimeMicros;

    public AcquireLeaseRequest() {
      this.kind = KIND;
    }

    public String mac;
  }

  /**
   * Release lease request.
   */
  public static class ReleaseLeaseRequest extends LeaseRequest {
    public static final String KIND = Utils.buildKind(ReleaseLeaseRequest.class);

    public ReleaseLeaseRequest() {
      this.kind = KIND;
    }

    public String mac;

    /**
     * Optional.
     * When set, the associated lease document is expected to have been deleted (e.g. via document expiration)
     */
    public String ip;
  }

  /**
   * This class defines the document state associated with a single
   * {@link DhcpSubnetService} instance.
   */
  public static class DhcpSubnetState extends ServiceDocument {
    /**
     * Subnet identifier.
     *
     * It is used as the basename for the subnet's self link.
     * Also used to generate composite lease keys, scoped to the subnet they belong to.
     */
    public String id;

    /**
     * Subnet CIDR.
     **/
    public String subnetAddress;

    /**
     * Ranges to assign dynamic IPs from.
     **/
    public Range[] ranges;

    /**
     * Ranges to distribute dynamic leases from.
     */
    public static class Range {
      /**
       * high address of the range.
       **/
      public String low;
      /**
       * low address of the range.
       **/
      public String high;

      /**
       * Infrastructure use.  List used to track which IPs have been used and are free.
       * This is set by the run-time.
       */
      public byte[] usedIps;
    }
  }

  public DhcpSubnetService() {
    super(DhcpSubnetState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      start.fail(new IllegalArgumentException("body is required"));
      return;
    }

    DhcpSubnetState state = start.getBody(DhcpSubnetState.class);
    if (!validate(start, state)) {
      return;
    }

    start.complete();
  }

  /**
   * Patch the subnet's configuration and/or ranges.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceDocument doc = patch.getBody(ServiceDocument.class);
    if (doc != null && doc.documentKind != null
        && doc.documentKind.equals(Utils.buildKind(DhcpSubnetState.class))) {
      handleSubnetPatch(patch);
      return;
    }

    if (handleLeasePatch(patch)) {
      return;
    }

    patch.fail(new IllegalArgumentException("unknown document type"));
  }

  /**
   * Handle an incoming PATCH request as a lease acquisition/release request.
   *
   * @param patch
   * @return if the operation was handled by this method.
   */
  private boolean handleLeasePatch(Operation patch) {
    // If "kind" is specified, interpret the PATCH as a lease operation.
    // Interpret it as a PATCH against the subnet itself otherwise.
    LeaseRequest request = patch.getBody(LeaseRequest.class);

    if (request.kind.equals(AcquireLeaseRequest.KIND)) {
      handleAcquireLeaseRequest(patch, patch.getBody(AcquireLeaseRequest.class));
      return true;
    }

    if (request.kind.equals(ReleaseLeaseRequest.KIND)) {
      handleReleaseLeaseRequest(patch, patch.getBody(ReleaseLeaseRequest.class));
      return true;
    }

    return false;
  }

  private void handleAcquireLeaseRequest(Operation subnetPatch, AcquireLeaseRequest body) {
    DhcpSubnetState dhcpSubnetState = getState(subnetPatch);

    /**
     * The flow for acquiring a lease:
     *
     *   1. Send PATCH updating the expiration time to (now + (lease timeout))
     *     - If successful, return new lease document
     *     - If not successful (404), proceed with step 2
     *   2. Find IP to assign to this lease
     *   3. Send POST to lease factory service to create new lease
     */
    ServiceUtils.logInfo(this, "handleAcquireLeaseRequest for subnet %s id: %s", dhcpSubnetState.subnetAddress,
        dhcpSubnetState.id);
    tryPatchLeaseState(dhcpSubnetState, body.mac, (op, ex) -> {
      if (ex == null) {
        // Lease was patched
        subnetPatch.setBody(op.getBody(DhcpLeaseService.DhcpLeaseState.class));
        subnetPatch.complete();
        return;
      }

      if (op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
        // Find an unused IP and POST a new lease to the factory
        postLeaseState(subnetPatch, dhcpSubnetState, body);
        return;
      }
      ServiceUtils.logWarning(this, "error patching %s: %d  in handleAcquireLeaseRequest for subnet %s id: %s",
          op.getUri(), op.getStatusCode(),
          dhcpSubnetState.subnetAddress,
          dhcpSubnetState.id);
      subnetPatch.fail(ex);
    });
  }

  private void tryPatchLeaseState(DhcpSubnetState subnetState, String mac,
                                  Operation.CompletionHandler handler) {
    DhcpLeaseService.DhcpLeaseState leasePatchBody = new DhcpLeaseService.DhcpLeaseState();

    // TODO(PN): This should be configurable on the subnet state.
    // https://www.pivotaltracker.com/story/show/93381470
    leasePatchBody.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
        + DEFAULT_LEASE_EXPIRATION_IN_MICROS;
    URI leaseFactory = UriUtils.buildUri(this.getHost(), DhcpLeaseServiceFactory.SELF_LINK);
    Operation leasePatch = Operation
        .createPatch(
            UriUtils.extendUri(leaseFactory,
                DhcpLeaseService.DhcpLeaseState.buildSelfLink(subnetState, mac)))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
        .addRequestHeader(Operation.PRAGMA_HEADER, Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setBody(leasePatchBody)
        .setCompletion(handler);

    sendRequest(leasePatch);
  }

  private void postLeaseState(Operation subnetPatch, DhcpSubnetState dhcpSubnetState,
                              AcquireLeaseRequest body) {
    DhcpLeaseService.DhcpLeaseState leasePostBody = new DhcpLeaseService.DhcpLeaseState();
    leasePostBody.documentExpirationTimeMicros = body.expirationTimeMicros != 0 ?
        body.expirationTimeMicros : Utils.getNowMicrosUtc()
        + DEFAULT_LEASE_EXPIRATION_IN_MICROS;
    leasePostBody.networkDescriptionLink = dhcpSubnetState.documentSelfLink;
    leasePostBody.mac = body.mac;
    leasePostBody.ip = getNextUnusedAddress(subnetPatch);
    if (leasePostBody.ip.isEmpty()) {
      subnetPatch.fail(new IllegalStateException("No address available in subnet"));
      return;
    }

    Operation leasePost = Operation
        .createPost(UriUtils.buildUri(getHost(), DhcpLeaseServiceFactory.class))
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
        .setBody(leasePostBody)
        .setCompletion(
            (op, ex) -> {
              if (ex != null) {
                ServiceUtils.logWarning(this, "Error POSTing lease: %s", ex.getMessage());
                subnetPatch.fail(ex);
                return;
              }

              DhcpLeaseService.DhcpLeaseStateWithDescription lwd = DhcpLeaseService.DhcpLeaseStateWithDescription
                  .create(op.getBody(DhcpLeaseService.DhcpLeaseState.class));
              lwd.description = dhcpSubnetState;
              subnetPatch.setBody(lwd);
              subnetPatch.complete();
            });

    sendRequest(leasePost);
  }

  /**
   * From the current state, iterate the IPRanges to find an unused address.
   * Update the current state with the appropriate bit set in the used array.
   *
   * @param subnetPatch
   * @return
   */
  private String getNextUnusedAddress(Operation subnetPatch) {
    DhcpSubnetState curSubnetState = getState(subnetPatch);
    // Pick first available address from first non-empty range
    for (DhcpSubnetState.Range range : curSubnetState.ranges) {
      IPRange ipRange = new IPRange(range);

      if (!ipRange.isFull()) {
        String nextIP = ipRange.getNextUnused().getHostAddress();
        range.usedIps = ipRange.toByteArray();
        return nextIP;
      }
    }

    return "";
  }

  // Finds the range the ip belongs in, and unsets the associated usedIP field.
  private void unSetIP(DhcpSubnetState subnetState, String ip, Operation op) {
    if (subnetState.ranges == null || subnetState.ranges.length == 0) {
      return;
    }

    InetAddress address = null;
    try {
      // InetAddress#getByName does not cause a (blocking) DNS lookup if the argument
      // is a formatted IP address.
      address = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      op.fail(e);
      return;
    }

    for (DhcpSubnetState.Range r : subnetState.ranges) {
      IPRange range = new IPRange(r);
      if (range.isInRange(address)) {
        range.unSetUsed(address);
        // Update the subnet range.
        r.usedIps = range.toByteArray();
        return;
      }
    }

    op.fail(new IllegalArgumentException(address.toString() + " isn't in subnet's range"));
  }

  // There are 2 paths which can release the lease.  Either the client release the lease by PATCHing
  // a release request to the subnet document, or the lease is about to expire and the lease expiration
  // task (created when the lease is POSTed) has PATCHed a release request.
  private void handleReleaseLeaseRequest(Operation subnetOp, ReleaseLeaseRequest body) {
    DhcpSubnetState curSubnetState = getState(subnetOp);

    URI leaseUri = UriUtils.buildUri(this.getHost(), DhcpLeaseServiceFactory.SELF_LINK + "/" +
        DhcpLeaseService.DhcpLeaseState.buildSelfLink(curSubnetState, body.mac));

    ServiceUtils.logInfo(this, "handleReleaseLeaseRequest for leaseURI %s on Subnet %s", leaseUri.toString(),
        curSubnetState.subnetAddress);
    // the lease expiration task is issuing the release request.  Since the task itself has supplied the IP
    // don't bother with a GET.
    if (body.ip != null) {
      unSetIP(curSubnetState, body.ip, subnetOp);
      sendRequest(Operation.createDelete(leaseUri)
          .setBody(new ServiceDocument()));
      subnetOp.complete();
    } else {
      sendRequest(Operation.createGet(leaseUri)
          .setCompletion((o, e) -> {
            if (e != null) {
              // already deleted?  Can that happen?
              subnetOp.fail(e);
              return;
            }
            DhcpLeaseService.DhcpLeaseState lease = o.getBody(DhcpLeaseService.DhcpLeaseState.class);
            unSetIP(curSubnetState, lease.ip, subnetOp);
            sendRequest(Operation.createDelete(leaseUri).setBody(
                new ServiceDocument()));
            subnetOp.complete();
          }));
    }
  }

  private void handleSubnetPatch(Operation patch) {
    DhcpSubnetState currentState = getState(patch);
    DhcpSubnetState patchState = patch.getBody(DhcpSubnetState.class);
    try {
      if (!validate(patch, patchState)) {
        return;
      }

      if (!patchState.subnetAddress.equals(currentState.subnetAddress)) {
        throw new IllegalArgumentException("subnetAddress mismatch");
      }

      if (patchState.ranges != null) {
        currentState.ranges = patchState.ranges;
      }

      patch.setBody(currentState).complete();
    } catch (IllegalArgumentException e) {
      patch.fail(e);
    }
  }

  /**
   * Verify the subnet document is valid.
   *
   * @param op Operation that caused validation to happen.
   * @param subnet SubnetServiceState to validate.
   * @return Whether or not the validation succeeded. If it did not succeed, the operation is marked as failed.
   * @throws IllegalArgumentException
   */
  public boolean validate(Operation op, DhcpSubnetState subnet) {
    try {
      DhcpUtils.isCIDR(subnet.subnetAddress);

      if (subnet.ranges != null) {
        for (DhcpSubnetState.Range o : subnet.ranges) {
          DhcpUtils.isValidInetAddress(o.low);
          DhcpUtils.isValidInetAddress(o.high);
        }
      }
    } catch (IllegalArgumentException e) {
      op.fail(e);
      return false;
    }

    return true;
  }
}
