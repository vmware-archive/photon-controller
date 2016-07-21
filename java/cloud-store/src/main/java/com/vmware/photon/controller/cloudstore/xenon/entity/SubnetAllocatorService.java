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
        new RequestRouter.RequestBodyMatcher<AllocateSubnet>(
            AllocateSubnet.class, "kind", AllocateSubnet.KIND),
        this::handleAllocateSubnet, "Allocate a subnet");

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<ReleaseSubnet>(
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

    //Input
    public String ownerProjectId;
    public Integer numberOfAllIpAddresses;
    public Integer numberOfStaticIpAddresses;

    //Output
    public String subnetId;

    private AllocateSubnet() {
      kind = null;
    }

    public AllocateSubnet(String ownerProjectId, Integer numberOfAllIpAddresses, Integer numberOfStaticIpAddresses) {
      if (ownerProjectId == null) {
        throw new IllegalArgumentException("ownerProjectId cannot be null");
      }

      if (numberOfAllIpAddresses == null) {
        throw new IllegalArgumentException("numberOfAllIpAddresses cannot be null");
      }

      if (numberOfStaticIpAddresses == null) {
        throw new IllegalArgumentException("numberOfStaticIpAddresses cannot be null");
      }

      this.kind = KIND;
      this.ownerProjectId = ownerProjectId;
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
    patch.complete();
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

    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("documentSelfLink", documentSelfLink)
          .add("rootCidr", rootCidr)
          .toString();
    }
  }
}
