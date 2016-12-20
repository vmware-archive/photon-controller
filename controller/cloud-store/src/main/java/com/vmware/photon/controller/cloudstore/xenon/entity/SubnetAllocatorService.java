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

import com.vmware.photon.controller.api.model.ReservedIpType;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.net.util.SubnetUtils;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;


/**
 * Used for carving out subnets from the network represented by rootCidr.
 */
public class SubnetAllocatorService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/subnet-allocators";

  /**
   * This is the well known location for the single instance of this service.
   * The single instance will be started at the time of configuration of virtual networking
   * in the system which could be at deployment time or later.
   */
  public static final String SINGLETON_LINK = FACTORY_LINK + "/root-subnet";

  public static FactoryService createFactory() {
    return FactoryService.create(SubnetAllocatorService.class, SubnetAllocatorService.State.class);
  }

  public SubnetAllocatorService() {
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
        new RequestRouter.RequestBodyMatcher<>(
            AllocateSubnet.class, "kind", AllocateSubnet.KIND),
        this::handleAllocateSubnet, "Allocate a subnet");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            ReleaseSubnet.class, "kind", ReleaseSubnet.KIND),
        this::handleReleaseSubnet, "Release a subnet");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  /**
   * Class for allocating a subnet from the available IP pool of the root CIDR.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class AllocateSubnet extends ServiceDocument {
    public static final String KIND = AllocateSubnet.class.getCanonicalName();
    public final String kind;

    public String subnetId;
    public Long numberOfAllIpAddresses;
    public Long numberOfStaticIpAddresses;
    // This switch is used to determine whether we want more logging in handle allocation.
    // We purposely disable it in unit test since we have a UT that generates a lot of small subnets,
    // and hence runs out of memory.
    // By default it is true in production. This gives us more insight of how the subnet allocation
    // algorithm performs. It should not cause memory issue since the log is directed to a rotating file.
    public Boolean verbose;

    private AllocateSubnet() {
      kind = null;
    }

    public AllocateSubnet(String subnetId, Long numberOfAllIpAddresses, Long numberOfStaticIpAddresses) {
      this(subnetId, numberOfAllIpAddresses, numberOfStaticIpAddresses, true);
    }

    public AllocateSubnet(String subnetId, Long numberOfAllIpAddresses, Long numberOfStaticIpAddresses,
      Boolean verbose) {
      if (subnetId == null) {
        throw new IllegalArgumentException("subnetId cannot be null");
      }

      if (numberOfAllIpAddresses == null) {
        throw new IllegalArgumentException("numberOfAllIpAddresses cannot be null");
      }

      if (numberOfStaticIpAddresses == null) {
        throw new IllegalArgumentException("numberOfStaticIpAddresses cannot be null");
      }

      // Includes network and broadcast IPs
      final long minSize = ReservedIpType.values().length + numberOfStaticIpAddresses + 2;
      if (numberOfAllIpAddresses < minSize) {
        throw new IllegalArgumentException(
            "numberOfAllIpAddresses should be at least (COUNT_OF_RESERVED_IPS + numberOfStaticIpAddresses " +
                " 1 for network address + 1 for broadcast address) = " +
                +minSize);
      }

      this.kind = KIND;
      this.subnetId = subnetId;
      this.numberOfAllIpAddresses = numberOfAllIpAddresses;
      this.numberOfStaticIpAddresses = numberOfStaticIpAddresses;
      this.verbose = verbose;
    }
  }

  /**
   * Class for releasing a subnet and returning its subnet range to the root subnet IP pool.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class ReleaseSubnet extends ServiceDocument {
    public static final String KIND = ReleaseSubnet.class.getCanonicalName();
    public final String kind;

    //Input
    public String subnetId;
    // This switch is used to determine whether we want more logging in handle allocation.
    // We purposely disable it in unit test since we have a UT that generates a lot of small subnets,
    // and hence runs out of memory.
    // By default it is true in production. This gives us more insight of how the subnet allocation
    // algorithm performs. It should not cause memory issue since the log is directed to a rotating file.
    public Boolean verbose;

    private ReleaseSubnet() {
      kind = null;
    }

    public ReleaseSubnet(String subnetId) {
      this(subnetId, true);
    }

    public ReleaseSubnet(String subnetId, Boolean verbose) {
      if (subnetId == null) {
        throw new IllegalArgumentException("subnetId cannot be null");
      }

      this.kind = KIND;
      this.subnetId = subnetId;
      this.verbose = verbose;
    }
  }

  public void handleAllocateSubnet(Operation patch) {
    AllocateSubnet allocateSubnetPatch = patch.getBody(AllocateSubnet.class);
    State currentState = getState(patch);
    ServiceUtils.logInfo(this, "Allocating subnet: %s", allocateSubnetPatch.subnetId);

    if (allocateSubnetPatch.verbose) {
      ServiceUtils.logInfo(this, "Subnet size to be allocated: %d", allocateSubnetPatch.numberOfAllIpAddresses);
    }

    try {
      Long requestedSize = allocateSubnetPatch.numberOfAllIpAddresses;
      List<IpV4Range> candidateRanges = currentState.freeList.stream()
          .filter(ipV4Range -> (ipV4Range.high - ipV4Range.low) >= requestedSize - 1)
          .sorted((left, right) -> Long.compare(left.low, right.low))
          .collect(Collectors.toList());
      int inverseSubnetMask = IpHelper.safeLongToInt(requestedSize - 1);
      int subnetMask = ~(inverseSubnetMask);

      if (allocateSubnetPatch.verbose) {
        currentState.freeList.stream().forEach(
            range -> ServiceUtils.logInfo(this, "Free range before allocation: %d to %d", range.low, range.high));
        candidateRanges.stream().forEach(
            range -> ServiceUtils.logInfo(this, "Candidate free range: %d to %d", range.low, range.high));
      }

      IpV4Range createdIpv4Range = null;
      IpV4Range selectedIpv4Range = null;

      for (IpV4Range ipV4Range : candidateRanges) {
        long currentLow = ipV4Range.low;

        while ((currentLow & subnetMask) != currentLow) {
          currentLow++;
        }

        long currentHigh = currentLow + inverseSubnetMask;

        if (currentHigh <= ipV4Range.high) {
          createdIpv4Range = new IpV4Range(currentLow, currentHigh);
          selectedIpv4Range = ipV4Range;
          break;
        }
      }

      if (createdIpv4Range == null) {
        patch.fail(new IllegalArgumentException("Could not find any IP range big enough to allocate"));
        return;
      }

      if (allocateSubnetPatch.verbose) {
        ServiceUtils.logInfo(this, "Selected range: %d to %d", selectedIpv4Range.low, selectedIpv4Range.high);
      }

      currentState.freeList.remove(selectedIpv4Range);
      if (createdIpv4Range.low > selectedIpv4Range.low) {
        IpV4Range lowRange = new IpV4Range(selectedIpv4Range.low, createdIpv4Range.low - 1);
        if (allocateSubnetPatch.verbose) {
          ServiceUtils.logInfo(this, "Insert low free range: %d to %d", lowRange.low, lowRange.high);
        }
        currentState.freeList.add(lowRange);
      }

      if (createdIpv4Range.high < selectedIpv4Range.high) {
        IpV4Range highRange = new IpV4Range(createdIpv4Range.high + 1, selectedIpv4Range.high);
        if (allocateSubnetPatch.verbose) {
          ServiceUtils.logInfo(this, "Insert high free range: %d to %d", highRange.low, highRange.high);
        }
        currentState.freeList.add(highRange);
      }

      DhcpSubnetService.State subnet = new DhcpSubnetService.State();
      subnet.cidr = IpHelper.calculateCidrFromIpV4Range(createdIpv4Range.low, createdIpv4Range.high);
      subnet.lowIp = createdIpv4Range.low;
      subnet.highIp = createdIpv4Range.high;

      int numReservedIps = ReservedIpType.values().length;
      subnet.reservedIpList = new HashMap<>(numReservedIps);
      for (int i = 0; i < numReservedIps; i++) {
        subnet.reservedIpList.put(ReservedIpType.values()[i], subnet.lowIp + 1 + i);
      }

      if (allocateSubnetPatch.numberOfStaticIpAddresses > 0) {
        subnet.lowIpStatic = subnet.lowIp + 1 + numReservedIps;
        subnet.highIpStatic = subnet.lowIpStatic + allocateSubnetPatch.numberOfStaticIpAddresses - 1;
        subnet.lowIpDynamic = subnet.highIpStatic + 1;
      } else {
        subnet.lowIpDynamic = subnet.lowIp + 1 + numReservedIps;
      }

      subnet.highIpDynamic = subnet.highIp - 1;
      subnet.subnetId = allocateSubnetPatch.subnetId;
      subnet.documentSelfLink = allocateSubnetPatch.subnetId;
      subnet.dhcpAgentEndpoint = currentState.dhcpAgentEndpoint;

      Operation postOperation = Operation.createPost(this, DhcpSubnetService.FACTORY_LINK)
          .setBody(subnet);
      ServiceUtils.doServiceOperation(this, postOperation);

      setState(patch, currentState);

      if (allocateSubnetPatch.verbose) {
        currentState.freeList.stream().forEach(
            range -> ServiceUtils.logInfo(this, "Free range after allocation: %d to %d", range.low, range.high));
      }
      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  public void handleReleaseSubnet(Operation patch) {
    ReleaseSubnet releaseSubnetPatch = patch.getBody(ReleaseSubnet.class);
    State currentState = getState(patch);
    ServiceUtils.logInfo(this, "Releaing subnet: %s", releaseSubnetPatch.subnetId);

    try {
      Operation getOperation =
          Operation.createGet(this, DhcpSubnetService.FACTORY_LINK + "/" + releaseSubnetPatch.subnetId);
      Operation completedOperation = null;
      try {
        completedOperation = ServiceUtils.doServiceOperation(this, getOperation);
      } catch (DocumentNotFoundException ex) {
        throw new IllegalArgumentException("Could not find the subnet service with the provided subnetId");
      }

      DhcpSubnetService.State subnetState = completedOperation.getBody(DhcpSubnetService.State.class);

      Operation deleteOperation =
          Operation.createDelete(this, subnetState.documentSelfLink);
      ServiceUtils.doServiceOperation(this, deleteOperation);

      if (releaseSubnetPatch.verbose) {
        currentState.freeList.stream().forEach(
            range -> ServiceUtils.logInfo(this, "Free range before release: %d to %d", range.low, range.high));
      }

      IpV4Range targetRange = new IpV4Range(subnetState.lowIp, subnetState.highIp);
      List<IpV4Range> currentFreeList = currentState.freeList
          .stream()
          .sorted((left, right) -> Long.compare(left.low, right.low))
          .collect(Collectors.toList());
      List<IpV4Range> newFreeList = new ArrayList<>();
      // Merge range
      for (IpV4Range freeRange : currentFreeList) {
        if (freeRange.high < targetRange.low - 1) {
          newFreeList.add(freeRange);
        } else if (freeRange.low > targetRange.high + 1) {
          newFreeList.add(targetRange);
          targetRange = freeRange;
        } else {
          targetRange =
              new IpV4Range(Math.min(targetRange.low, freeRange.low), Math.max(targetRange.high, freeRange.high));
        }
      }

      newFreeList.add(targetRange);
      currentState.freeList = newFreeList;

      if (releaseSubnetPatch.verbose) {
        currentState.freeList.stream().forEach(
            range -> ServiceUtils.logInfo(this, "Free range after release: %d to %d", range.low, range.high));
      }

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
      ValidationUtils.validateState(startState);

      seedWithOneAvailableSubnet(startState.rootCidr, startState);

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
   * Persistent subnet allocator state data.
   */
  @MigrateDuringUpgrade(transformationServicePath = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = FACTORY_LINK,
      destinationFactoryServicePath = FACTORY_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = FACTORY_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @ServiceDocument.IndexingParameters(versionRetention = 100)
  public static class State extends ServiceDocument {

    /**
     * This is the root network CIDR from which subnets will be allocated.
     */
    @Immutable
    public String rootCidr;

    /**
     * This is the list of free ranges available for subnet allocation.
     * We should not index this otherwise we hit lucene limit of field size that will limit
     * us to around 300 items in the free list.
     */
    @ServiceDocument.PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY)
    public Collection<IpV4Range> freeList;

    /**
     * Endpoint for communicating with DHCP agent.
     * Endpoint includes: IP, port and protocol.
     */
    public String dhcpAgentEndpoint;

    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("rootCidr", rootCidr)
          .toString();
    }
  }

  private void seedWithOneAvailableSubnet(String rootCidr, State startState)
      throws InterruptedException, TimeoutException, BadRequestException,
      DocumentNotFoundException, UnknownHostException {

    SubnetUtils subnetUtils = new SubnetUtils(rootCidr);
    subnetUtils.setInclusiveHostCount(true);
    SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
    Long lowIp = IpHelper.ipStringToLong(subnetInfo.getLowAddress());
    Long highIp = IpHelper.ipStringToLong(subnetInfo.getHighAddress());
    IpV4Range ipV4Range = new IpV4Range(lowIp, highIp);

    if (startState.freeList == null) {
      startState.freeList = new ArrayList<>();
    }

    startState.freeList.add(ipV4Range);
  }

  /**
   * POJO to store a range of IPv4 addresses.
   */
  public static class IpV4Range {
    public IpV4Range(Long low, Long high) {
      this.low = low;
      this.high = high;
    }

    public Long low;
    public Long high;
  }
}
