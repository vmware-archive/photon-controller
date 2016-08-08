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

import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;


/**
 * Used for persisting the virtual network information.
 */
public class VirtualNetworkService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/virtual-networks";

  public static FactoryService createFactory() {
    return FactoryService.create(VirtualNetworkService.class, VirtualNetworkService.State.class);
  }

  public VirtualNetworkService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      ValidationUtils.validateState(startState);
      startOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());

    try {
      State currentState = getState(patchOperation);

      State patchState = patchOperation.getBody(State.class);
      validatePatchState(currentState, patchState);

      PatchUtils.patchState(currentState, patchState);
      ValidationUtils.validateState(currentState);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.logInfo(this, "Deleting service %s", getSelfLink());

    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  private void validatePatchState(State startState, State patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(startState, patchState);
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

    public static final String FIELD_NAME_LOGICAL_SWITCH_ID = "logicalSwitchId";

    @NotBlank
    @WriteOnce
    public String name;

    public String description;

    @NotNull
    public SubnetState state;

    /**
     * ID of the parent object this virtual network belongs to.
     */
    @Immutable
    public String parentId;

    /**
     * Type of the parent object this virtual network belongs to.
     */
    @Immutable
    public String parentKind;

    /**
     * This value represents requested IP count for the network.
     */
    @NotNull
    @Immutable
    public Integer size;

    /**
     * Indicates whether this network is the default one, confined in the level of its parentId.
     */
    public Boolean isDefault;

    /**
     * Whether this network is isolated or connected to outside.
     */
    @NotNull
    @WriteOnce
    public RoutingType routingType;

    /**
     * This is the CIDR of the virtual network.
     */
    @WriteOnce
    public String cidr;

    /**
     * This is the smallest dynamic IP of the available dynamic IP range.
     */
    @WriteOnce
    public String lowIpDynamic;

    /**
     * This is the biggest dynamic IP of the available dynamic IP range.
     */
    @WriteOnce
    public String highIpDynamic;

    /**
     * This is a list of IPs reserved for infrastructure use.
     */
    @WriteOnce
    public List<String> reservedIpList;

    /**
     * This is the smallest static IP of the available static IP range.
     */
    @WriteOnce
    public String lowIpStatic;

    /**
     * This is the biggest static IP of the available static IP range.
     */
    @WriteOnce
    public String highIpStatic;

    /**
     * ID of the nsx logical switch.
     */
    @WriteOnce
    public String logicalSwitchId;

    /**
     * ID of the nsx tier1 logical router.
     */
    @WriteOnce
    public String logicalRouterId;

    /**
     * ID of the nsx tier0 logical router.
     */
    @WriteOnce
    public String tier0RouterId;

    /**
     * ID of the port on the logical switch that connects to the tier1 logical router.
     */
    @WriteOnce
    public String logicalSwitchUplinkPortId;

    /**
     * Mapping between VM and the downlink ports on the logical switch.
     */
    public Map<String, String> logicalSwitchDownlinkPortIds;

    /**
     * ID of the port on the tier1 logical router that connects to the logical switch.
     */
    @WriteOnce
    public String logicalRouterDownlinkPortId;

    /**
     * ID of the port on the tier1 logical router that connects to the tier0 logical router.
     */
    @WriteOnce
    public String logicalRouterUplinkPortId;

    /**
     * ID of the port on the tier0 logical router that connects to the tier1 logical router.
     */
    public String tier0RouterDownlinkPortId;

    /**
     * Mapping between the ID of the VM and the NAT rule ID. It is used to track the floating IP
     * configuration of the VM.
     */
    public Map<String, String> vmIdToNatRuleIdMap;

    public Long deleteRequestTime;

    @Override
    public String toString() {
      return com.google.common.base.Objects.toStringHelper(this)
          .add("name", name)
          .add("description", description)
          .add("state", state)
          .add("parentId", parentId)
          .add("parentKind", parentKind)
          .add("size", size)
          .add("routingType", routingType)
          .add("cidr", cidr)
          .add("lowIpDynamic", lowIpDynamic)
          .add("highIpDynamic", highIpDynamic)
          .add("reservedIpList", reservedIpList)
          .add("lowIpStatic", lowIpStatic)
          .add("highIpStatic", highIpStatic)
          .add("logicalSwitchId", logicalSwitchId)
          .add("logicalRouterId", logicalRouterId)
          .add("tier0RouterId", tier0RouterId)
          .add("logicalSwitchUplinkPortId", logicalSwitchUplinkPortId)
          .add("logicalSwitchDownlinkPortIds", logicalSwitchDownlinkPortIds)
          .add("logicalRouterDownlinkPortId", logicalRouterDownlinkPortId)
          .add("logicalRouterUplinkPortId", logicalRouterUplinkPortId)
          .add("tier0RouterDownlinkPortId", tier0RouterDownlinkPortId)
          .add("documentSelfLink", documentSelfLink)
          .toString();
    }
  }
}
