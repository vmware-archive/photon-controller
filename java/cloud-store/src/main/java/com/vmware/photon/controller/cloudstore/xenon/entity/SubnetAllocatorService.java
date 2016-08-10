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
import com.vmware.xenon.common.StatefulService;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.util.SubnetUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
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

    private AllocateSubnet() {
      kind = null;
    }

    public AllocateSubnet(String subnetId, Long numberOfAllIpAddresses, Long numberOfStaticIpAddresses) {
      if (subnetId == null) {
        throw new IllegalArgumentException("subnetId cannot be null");
      }

      if (numberOfAllIpAddresses == null) {
        throw new IllegalArgumentException("numberOfAllIpAddresses cannot be null");
      }

      if (numberOfStaticIpAddresses == null) {
        throw new IllegalArgumentException("numberOfStaticIpAddresses cannot be null");
      }

      this.kind = KIND;
      this.subnetId = subnetId;
      this.numberOfAllIpAddresses = numberOfAllIpAddresses;
      this.numberOfStaticIpAddresses = numberOfStaticIpAddresses;
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

    private ReleaseSubnet() {
      kind = null;
    }

    public ReleaseSubnet(String subnetId) {
      if (subnetId == null) {
        throw new IllegalArgumentException("subnetId cannot be null");
      }

      this.kind = KIND;
      this.subnetId = subnetId;
    }
  }

  public void handleAllocateSubnet(Operation patch) {
    ServiceUtils.logInfo(this, "Allocating subnet %s", getSelfLink());

    AllocateSubnet allocateSubnetPatch = patch.getBody(AllocateSubnet.class);
    State currentState = getState(patch);


    try {
      Long requestedSize = allocateSubnetPatch.numberOfAllIpAddresses;
      List<IpV4Range> candidateRanges = currentState.freeList.stream()
          .filter(ipV4Range -> (ipV4Range.high - ipV4Range.low) >= requestedSize)
          .collect(Collectors.toList());
      int inverseSubnetMask = safeLongToInt(requestedSize - 1);
      int subnetMask = ~(inverseSubnetMask);

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

      currentState.freeList.remove(selectedIpv4Range);
      if (createdIpv4Range.low > selectedIpv4Range.low) {
        IpV4Range lowRange = new IpV4Range(selectedIpv4Range.low, createdIpv4Range.low - 1);
        currentState.freeList.add(lowRange);
      }

      if (createdIpv4Range.high < selectedIpv4Range.high) {
        IpV4Range highRange = new IpV4Range(createdIpv4Range.high + 1, selectedIpv4Range.high);
        currentState.freeList.add(highRange);
      }

      setState(patch, currentState);
      patch.complete();

      DhcpSubnetService.State subnet = new DhcpSubnetService.State();
      subnet.cidr = IpHelper.calculateCidrFromIpV4Range(createdIpv4Range.low, createdIpv4Range.high);
      subnet.lowIp = createdIpv4Range.low;
      subnet.highIp = createdIpv4Range.high;
      subnet.documentSelfLink = allocateSubnetPatch.subnetId;

      Operation postOperation = Operation.createPost(this, DhcpSubnetService.FACTORY_LINK)
          .setBody(subnet);
      ServiceUtils.doServiceOperation(this, postOperation);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  public void handleReleaseSubnet(Operation patch) {
    ServiceUtils.logInfo(this, "Releasing subnet %s", getSelfLink());
    patch.complete();
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
  public static class State extends ServiceDocument {

    /**
     * This is the root network CIDR from which subnets will be allocated.
     */
    @Immutable
    public String rootCidr;

    //    public Collection<AbstractMap.SimpleEntry<Long, Long>> freeList;
    public Collection<IpV4Range> freeList;

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
    SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
    Long lowIp, highIp;

    InetAddress lowIpAddress = InetAddresses.forString(subnetInfo.getLowAddress());
    if (lowIpAddress instanceof Inet4Address) {
      lowIp = IpHelper.ipToLong((Inet4Address) lowIpAddress);
    } else {
      throw new IllegalArgumentException("lowIpAddress not an IPv4 address");
    }

    InetAddress highIpAddress = InetAddresses.forString(subnetInfo.getHighAddress());
    if (highIpAddress instanceof Inet4Address) {
      highIp = IpHelper.ipToLong((Inet4Address) highIpAddress);
    } else {
      throw new IllegalArgumentException("highIpAddress not an IPv4 address");
    }

    IpV4Range ipV4Range = new IpV4Range(lowIp, highIp);

    if (startState.freeList == null) {
      startState.freeList = new ArrayList<>();
    }

    startState.freeList.add(ipV4Range);
  }

  private static int safeLongToInt(long l) {
    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(l
          + " cannot be cast to int without changing its value.");
    }
    return (int) l;
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
