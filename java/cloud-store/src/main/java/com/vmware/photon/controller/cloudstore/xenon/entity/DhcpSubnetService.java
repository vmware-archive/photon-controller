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
import com.vmware.photon.controller.common.IpHelper;
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
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Range;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.util.BitSet;
import java.util.List;

/**
 * Used for allocating IPs from a subnet and also to track ownership of a CIDR range by a network.
 */
public class DhcpSubnetService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/dhcp-subnets";

  /**
   * This is the well known location for the single instance of this service.
   * The single instance will be started at the time of configuration of virtual networking
   * in the system which could be at deployment time or later.
   */
  public static final String FLOATING_IP_SUBNET_SINGLETON_LINK = FACTORY_LINK + "/floating-ip-dhcp-subnet";

  public static final long MAX_IPV4 = 0xFFFFFFFFL; // this represents 255.255.255.255

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
        new RequestRouter.RequestBodyMatcher<>(
            IpOperationPatch.class, "kind", IpOperationPatch.Kind.AllocateIpToMac),
        this::handleAllocateIpToMacPatch, "Allocate IP to MAC address");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            IpOperationPatch.class, "kind", IpOperationPatch.Kind.ReleaseIpForMac),
        this::handleReleaseIpPatch, "Release Ip lease for the provided IP address");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  public void handleAllocateIpToMacPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to allocate IP to MAC", getSelfLink());

    try {
      String allocatedIp;
      IpOperationPatch ipOperationPatch = patch.getBody(IpOperationPatch.class);
      State currentState = getState(patch);

      if (currentState.ipAllocations.length() >= currentState.highIpDynamic - currentState.lowIpDynamic) {
        throw new IllegalArgumentException("range is full");
      }

      int cur = currentState.ipAllocations.nextClearBit(0);
      currentState.ipAllocations.set(cur);

      allocatedIp = IpHelper.longToIpString(cur + currentState.lowIpDynamic);

      IpLeaseService.State ipLease = new IpLeaseService.State();
      ipLease.macAddress = ipOperationPatch.macAddress;
      ipLease.ip = allocatedIp;
      ipLease.documentSelfLink = makeIpLeaseUrl(currentState.isFloatingIpSubnet, currentState.subnetId, allocatedIp);

      Operation postOperation = Operation
          .createPost(this, IpLeaseService.FACTORY_LINK)
          .setBody(ipLease);
      ServiceUtils.doServiceOperation(this, postOperation);
      ipOperationPatch.ipAddress = allocatedIp;

      currentState.version++;
      setState(patch, currentState);

      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  public void handleReleaseIpPatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s to release IP for MAC", getSelfLink());
    try {
      State currentState = getState(patch);
      IpOperationPatch ipOperationPatch = patch.getBody(IpOperationPatch.class);

      String ipLeaseLink =
          makeIpLeaseUrl(currentState.isFloatingIpSubnet, currentState.subnetId, ipOperationPatch.ipAddress);

      long ipToRelease = IpHelper.ipStringToLong(ipOperationPatch.ipAddress);

      Operation deleteOperation = Operation
          .createDelete(this, ipLeaseLink);
      ServiceUtils.doServiceOperation(this, deleteOperation);

      currentState.ipAllocations.clear((int) (ipToRelease - currentState.lowIpDynamic));

      currentState.version++;
      setState(patch, currentState);
      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    try {
      State startState = createOperation.getBody(State.class);

      InitializationUtils.initialize(startState);
      startState.size = startState.highIp - startState.lowIp + 1;
      if (startState.isFloatingIpSubnet) {
        startState.lowIpDynamic = startState.lowIp;
        startState.highIpDynamic = startState.highIp;
      }
      ValidationUtils.validateState(startState);

      Preconditions.checkArgument(startState.lowIp < startState.highIp, "lowIp should be less than highIp");

      Preconditions.checkArgument(StringUtils.isNotBlank(startState.cidr),
          "cidr should not be blank for an allocated subnet");

      if (!startState.isFloatingIpSubnet) {
        Preconditions.checkArgument(StringUtils.isNotBlank(startState.subnetId),
            "subnet should not be blank for an allocated private ip subnet");
      }

      startState.ipAllocations = new BitSet();

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
   * Class for allocating an available IP to the provided MAC address.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class IpOperationPatch extends ServiceDocument {
    public final Kind kind;
    public String macAddress;
    public String ipAddress;

    private IpOperationPatch() {
      kind = null;
    }

    public IpOperationPatch(Kind kind, String macAddress, String ipAddress) {
      if (kind == null) {
        throw new IllegalArgumentException("kind cannot be null");
      }

      if (kind == Kind.AllocateIpToMac && macAddress == null) {
        throw new IllegalArgumentException("macAddress cannot be null for allocate ip operation");
      }

      if (kind == Kind.ReleaseIpForMac && ipAddress == null) {
        throw new IllegalArgumentException("ipAddress cannot be null for release ip operation");
      }

      this.kind = kind;
      this.macAddress = macAddress;
      this.ipAddress = ipAddress;
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
    @NotNull
    @Range(min = 0, max = MAX_IPV4)
    public Long lowIp;

    /**
     * This is the biggest IP of this subnet. It is reserved as Broadcast Address.
     */
    @NotNull
    @Range(min = 0L, max = MAX_IPV4)
    public Long highIp;

    /**
     * This is the smallest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    @Range(min = 0L, max = MAX_IPV4)
    public Long lowIpDynamic;

    /**
     * This is the biggest IP of the range from which IPs will be allocated to VMs/MACs.
     */
    @Range(min = 0L, max = MAX_IPV4)
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
    @Range(min = 0L, max = MAX_IPV4)
    public Long lowIpStatic;

    /**
     * This is the biggest IP of the range from which IPs will be excluded for allocations to VMs/MACs.
     * This is calculated only for display purposes for the user.
     */
    @Range(min = 0L, max = MAX_IPV4)
    public Long highIpStatic;

    /**
     * This is a calculated field based on the difference of highIp and lowIp however it is still
     * persisted so that we can do queries on the index to find an available subnet from which to extract
     * a new subnet of smaller or equal size.
     */
    @DefaultLong(0L)
    @NotNull
    @Range(min = 0L, max = MAX_IPV4)
    public Long size;

    /**
     * This is the flag to indicate if the garbage collection service should perform on this document.
     */
    public boolean doGarbageCollection;

    /**
     * This version number represents the current version of the subnet based on changes in IP leases.
     * It will be patched for increment on each IP lease change.
     */
    public long version;

    /**
     * This version number represents the subnet version selected for pushing changes to DHCP agent.
     */
    public long versionStaged;

    /**
     * This version number represents the subnet version for which changes in IP leases are pushed
     * successfully to DHCP agent.
     */
    public long versionPushed;

    /**
     * This is the same id as the VirtualNetworkService that this subnet is associated with
     * in a one-to-one relationship.
     */
    public String subnetId;

    /**
     * Each bit in this bitset represents one IP address in the range.
     * A set bit indicates the mapping IP address is allocated.
     * An unset bit indicates the mapping IP address is available for allocation.
     * The lowest IP address in the range is mapped to bit index 0.
     */
    public BitSet ipAllocations;

    /**
     * This flag indicates if the subnet range is being used to manage the floating IP addresses or not.
     */
    @DefaultBoolean(false)
    @NotNull
    public Boolean isFloatingIpSubnet;

    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("cidr", cidr)
          .toString();
    }
  }

  public static String makeIpLeaseUrl(Boolean isFloatingIp, String subnetId, String ipAddress) {
    if (isFloatingIp) {
      return IpLeaseService.FACTORY_LINK + "/" + ipAddress.replace(".", ":");
    } else {
      return IpLeaseService.FACTORY_LINK + "/" + subnetId + ":" + ipAddress.replace(".", ":");
    }
  }
}
