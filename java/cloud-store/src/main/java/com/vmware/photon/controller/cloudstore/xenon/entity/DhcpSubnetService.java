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
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.List;


/**
 * Used for allocating IPs from a subnet and also to track ownership of a CIDR range by a network.
 */
public class DhcpSubnetService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/dhcp-subnets";

  public static FactoryService createFactory() {
    return FactoryService.create(DhcpSubnetService.class, DhcpSubnetService.State.class);
  }

  public DhcpSubnetService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public OperationProcessingChain getOperationProcessingChain() {
    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter myRouter = new RequestRouter();

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<IpOperationPatch>(
            IpOperationPatch.class, "kind", IpOperationPatch.Kind.AllocateIpToMac),
        this::handleAllocateIpToMacPatch, "Allocate IP to MAC address");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<IpOperationPatch>(
            IpOperationPatch.class, "kind", IpOperationPatch.Kind.ReleaseIpForMac),
        this::handleReleaseIpForMacPatch, "Release Ip for MAC address");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  /**
   * Class for allocating an available IP to the provided MAC address.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class IpOperationPatch extends ServiceDocument {
    /**
     * Defines type of IP operations that are supported.
     */
    public enum Kind {
      AllocateIpToMac,
      ReleaseIpForMac
    };
    public final Kind kind;
    public String macAddress;

    private IpOperationPatch() {
      kind = null;
    }

    public IpOperationPatch(Kind kind, String macAddress) {
      if (kind == null) {
        throw new IllegalArgumentException("kind cannot be null");
      }

      if (macAddress == null) {
        throw new IllegalArgumentException("macAddress cannot be null");
      }

      this.kind = kind;
      this.macAddress = macAddress;
    }
  }

  public void handleAllocateIpToMacPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
  }

  public void handleReleaseIpForMacPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
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
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.logInfo(this, "Deleting service %s", getSelfLink());
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * Persistent virtual network state data.
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
     * This is the CIDR allocated to the subnet.
     */
    @Immutable
    public String cidr;

    /**
     * This is the smallest IP of this subnet. It is reserved as Network Address.
     */
    @Immutable
    public long lowIp;

    /**
     * This is the biggest IP of this subnet. It is reserved as Broadcast Address.
     */
    @Immutable
    public long highIp;

    /**
     * This is the smallest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    @Immutable
    public long lowIpDynamic;

    /**
     * This is the biggest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    @Immutable
    public long highIpDynamic;

    /**
     * This is a list of IPs reserved for infrastructure use e.g. address for DHCP Relay router for the subnet.
     * We will include the lowIp and highIp in this list. This is calculated only for display purposes for the user.
     */
    @Immutable
    public List<Long> reservedIpList;

    /**
     * This is the smallest IP of the range from which IPs will be excluded for allocations to VMs/MACs.
     * This is calculated only for display purposes for the user.
     */
    @Immutable
    public long lowIpStatic;

    /**
     * This is the biggest IP of the range from which IPs will be excluded for allocations to VMs/MACs.
     * This is calculated only for display purposes for the user.
     */
    @Immutable
    public long highIpStatic;


    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("cidr", cidr)
          .toString();
    }
  }
}
