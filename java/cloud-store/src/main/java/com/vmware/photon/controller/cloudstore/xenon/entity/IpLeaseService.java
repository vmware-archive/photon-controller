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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.lang3.StringUtils;
import static com.google.common.base.Preconditions.checkArgument;


/**
 * Used for tracking allocations/leases of IPs of a given subnet.
 */
public class IpLeaseService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/ip-leases";

  public static FactoryService createFactory() {
    FactoryService factoryService = FactoryService.create(IpLeaseService.class, IpLeaseService.State.class);
    // We need symmetric replication so that we are not forced to do broadcast queries when we are pushing
    // lease information to the dhcp agent. We expect local queries would eventually capture all leases needed
    // to be pushed out.
    factoryService.setPeerNodeSelectorPath(ServiceUriPaths.NODE_SELECTOR_FOR_SYMMETRIC_REPLICATION);
    return factoryService;
  }

  public IpLeaseService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    try {
      State startState = createOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      ValidationUtils.validateState(startState);
      createOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, createOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      createOperation.fail(t);
    }
  }

  @Override
  public OperationProcessingChain getOperationProcessingChain() {
    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter myRouter = new RequestRouter();
    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            IpLeaseOperationPatch.class, "kind", IpLeaseOperationPatch.Kind.ACQUIRE),
        this::handleAcquireIpLease, "Acquire Ip lease");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            IpLeaseOperationPatch.class, "kind", IpLeaseOperationPatch.Kind.RELEASE),
        this::handleReleaseIpLease, "Release Ip lease");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.logInfo(this, "Deleting service %s", getSelfLink());
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  public void handleAcquireIpLease(Operation patchOperation) {
    ServiceUtils.logInfo(this, "handleAcquireIpLease invoked for service: %s", getSelfLink());
    IpLeaseOperationPatch ipLeaseOperationPatch = patchOperation.getBody(IpLeaseOperationPatch.class);
    State currentState = getState(patchOperation);

    if (StringUtils.isBlank(currentState.ownerVmId)) {
      // the ownerVmId is already cleaned up
      currentState.ownerVmId = ipLeaseOperationPatch.ownerVmId;
      currentState.macAddress = ipLeaseOperationPatch.macAddress;
    } else {

      if (currentState.ownerVmId.equalsIgnoreCase(ipLeaseOperationPatch.ownerVmId)) {
        patchOperation.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      } else {
        throw new IllegalArgumentException("The lease is already ACQUIRED by Vm: " + currentState.ownerVmId);
      }
    }

    setState(patchOperation, currentState);
    patchOperation.complete();
  }

  public void handleReleaseIpLease(Operation patchOperation) {
    ServiceUtils.logInfo(this, "handleReleaseIpLease invoked for service: %s", getSelfLink());
    IpLeaseOperationPatch ipLeaseOperationPatch = patchOperation.getBody(IpLeaseOperationPatch.class);

    State currentState = getState(patchOperation);
    if (StringUtils.isBlank(currentState.ownerVmId)) {
      // the ownerVmId is already cleaned up
      patchOperation.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    } else {
      // if the release requester does not have the same ownerVmId of the IpLease then throw BadRequestException
      checkArgument(currentState.ownerVmId.equalsIgnoreCase(ipLeaseOperationPatch.ownerVmId),
          "Current ownerVmId: %s, Request ownerVmId: %s, selflink: %s",
          currentState.ownerVmId, ipLeaseOperationPatch.ownerVmId, currentState.documentSelfLink);

      //release ownerVmId of the ip lease
      currentState.ownerVmId = null;
      currentState.macAddress = null;
    }

    setState(patchOperation, currentState);
    patchOperation.complete();
  }

  /**
   * Class for defining operations on ip lease.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class IpLeaseOperationPatch extends ServiceDocument {
    public final Kind kind;
    public final String ownerVmId;
    public final String macAddress;

    private IpLeaseOperationPatch() {
      kind = null;
      ownerVmId = null;
      macAddress = null;
    }

    public IpLeaseOperationPatch(Kind kind, String ownerVmId, String macAddress) {
      if (kind == null) {
        throw new IllegalArgumentException("kind cannot be null");
      }

      if (StringUtils.isBlank(ownerVmId)) {
        throw new IllegalArgumentException("ownerVmId cannot be blank");
      }

      if (kind == Kind.ACQUIRE) {
        if (StringUtils.isBlank(macAddress)) {
          throw new IllegalArgumentException("macAddress cannot be blank for an ACQUIRE request");
        }
      }

      this.kind = kind;
      this.ownerVmId = ownerVmId;
      this.macAddress = macAddress;
    }

    /**
     * Defines type of IP lease operations that are supported.
     */
    public enum Kind {
      ACQUIRE,
      RELEASE
    }
  }

  /**
   * Persistent IP lease state data.
   */
  @MigrateDuringUpgrade(transformationServicePath = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = FACTORY_LINK,
      destinationFactoryServicePath = FACTORY_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = FACTORY_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    /**
     * This is the owner network. We need this to be indexed so that we can do queries for all allocations for a given
     * network.
     */
    @Immutable
    @NotBlank
    public String subnetId;

    /**
     * This is IP for a given subnet that can be allocated to a MAC address.
     */
    @Immutable
    @NotBlank
    public String ip;

    /**
     * This is the MAC address to which the IP is allocated.
     */
    public String macAddress;

    /**
     * This is the vm to which the IP is allocated. Null value indicates the IP is available for
     * allocation to a new vm.
     */
    public String ownerVmId;

    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("subnetId", subnetId)
          .add("ip", ip)
          .add("macAddress", macAddress)
          .add("ownerVmId", ownerVmId)
          .toString();
    }
  }
}
