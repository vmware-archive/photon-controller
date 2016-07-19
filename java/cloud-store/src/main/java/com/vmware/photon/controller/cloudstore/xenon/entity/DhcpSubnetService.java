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
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
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

  public DhcpSubnetService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public static FactoryService createFactory() {
    return FactoryService.create(DhcpSubnetService.class, DhcpSubnetService.State.class);
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

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<SubnetOperationPatch>(
            SubnetOperationPatch.class, "kind", SubnetOperationPatch.Kind.ExtractSubnetFromBottom),
        this::handleReleaseIpForMacPatch, "Extract subnet from bottom");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<SubnetOperationPatch>(
            SubnetOperationPatch.class, "kind", SubnetOperationPatch.Kind.ExpandSubnetAtBottom),
        this::handleReleaseIpForMacPatch, "Expand subnet at bottom");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<SubnetOperationPatch>(
            SubnetOperationPatch.class, "kind", SubnetOperationPatch.Kind.ExpandSubnetAtTop),
        this::handleReleaseIpForMacPatch, "Expand subnet at top");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  public void handleAllocateIpToMacPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to allocate IP to MAC", getSelfLink());
  }

  public void handleReleaseIpForMacPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to release IP for MAC", getSelfLink());
  }

  public void handleExtractSubnet(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to extract subnet", getSelfLink());
  }

  public void handleExpandSubnetAtBottom(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to expand available subnet at bottom", getSelfLink());
  }

  public void handleExpandSubnetAtTop(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to expand available subnet at top", getSelfLink());
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

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logWarning(this, "Patching service %s using default handler not allowed", getSelfLink());
    patchOperation.fail(Operation.STATUS_CODE_BAD_METHOD);
  }


  /**
   * Class for resizing the subnet to support extraction and coalescing of subnets.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class SubnetOperationPatch extends ServiceDocument {
    public final Kind kind;;
    public Integer size;
    private SubnetOperationPatch() {
      kind = null;
    }

    public SubnetOperationPatch(Kind kind, Integer size) {
      if (kind == null) {
        throw new IllegalArgumentException("kind should not be null");
      }

      if (size == null) {
        throw new IllegalArgumentException("size should not be null");
      }

      if (size <= 0) {
        throw new IllegalArgumentException("size should be greater than zero");
      }

      this.kind = kind;
      this.size = size;
    }

    /**
     * Defines type of Subnet operations that are supported.
     */
    public enum Kind {
      ExtractSubnetFromBottom,
      ExpandSubnetAtTop,
      ExpandSubnetAtBottom
    }
  }

  /**
   * Class for allocating an available IP to the provided MAC address.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class IpOperationPatch extends ServiceDocument {
        public final Kind kind;;
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

    /**
     * Defines type of IP operations that are supported.
     */
    public enum Kind {
      AllocateIpToMac,
      ReleaseIpForMac
    }
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
    public String cidr;

    /**
     * This is the smallest IP of this subnet. It is reserved as Network Address.
     */
    public Long lowIp;

    /**
     * This is the biggest IP of this subnet. It is reserved as Broadcast Address.
     */
    public Long highIp;

    /**
     * This is the smallest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    public Long lowIpDynamic;

    /**
     * This is the biggest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    public Long highIpDynamic;

    /**
     * This is a list of IPs reserved for infrastructure use e.g. address for DHCP Relay router for the subnet.
     * We will include the lowIp and highIp in this list. This is calculated only for display purposes for the user.
     */
    public List<Long> reservedIpList;

    /**
     * This is the smallest IP of the range from which IPs will be excluded for allocations to VMs/MACs.
     * This is calculated only for display purposes for the user.
     */
    public Long lowIpStatic;

    /**
     * This is the biggest IP of the range from which IPs will be excluded for allocations to VMs/MACs.
     * This is calculated only for display purposes for the user.
     */
    public Long highIpStatic;

    /**
     * This flag indicates if the subnet range is available for extracting subnets that take up
     * all or part of the range.
     */
    @DefaultBoolean(false)
    public boolean isAllocated;

    /**
     * This is a calculated field based on the difference of highIp and lowIp however it is still
     * persisted so that we can do queries on the index to find an available subnet from which to extract
     * a new subnet of smaller or equal size.
     */
    @DefaultInteger(0)
    public Integer size;


    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("cidr", cidr)
          .toString();
    }
  }
}
