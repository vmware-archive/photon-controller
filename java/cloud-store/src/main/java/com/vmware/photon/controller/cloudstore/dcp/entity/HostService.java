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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotEmpty;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Range;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing an ESX host.
 */
public class HostService extends StatefulService {

  private final Random random = new Random();

  public HostService() {
    super(State.class);
    super.toggleOption(ServiceOption.ENFORCE_QUORUM, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);

      if (null == startState.schedulingConstant) {
        startState.schedulingConstant = (long) random.nextInt(10 * 1000);
      }

      validateState(startState);
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());

    try {
      State startState = getState(patchOperation);

      State patchState = patchOperation.getBody(State.class);
      validatePatchState(startState, patchState);

      PatchUtils.patchState(startState, patchState);
      validateState(startState);

      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument template = super.getDocumentTemplate();
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_REPORTED_DATASTORES);
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_REPORTED_NETWORKS);
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_USAGE_TAGS);
    ServiceUtils.setSortedIndexing(template, State.FIELD_NAME_SCHEDULING_CONSTANT);
    return template;
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);

    if (currentState.usageTags.contains(UsageTag.MGMT.name())) {

      checkState(null != currentState.metadata);

      // Mandatory metadata for management host
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE);
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER);
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY);
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP);
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK);
      validateManagementMetadata(currentState, State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP);

      // Optional meatadata for management host
      boolean hasVmResourceOverwrite = false;
      boolean allVmResourceOverwrite = true;
      for (String key : Arrays.asList(
          State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE,
          State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE,
          State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE)) {
        boolean overwrite = currentState.metadata.containsKey(key);
        allVmResourceOverwrite = allVmResourceOverwrite && overwrite;
        hasVmResourceOverwrite = hasVmResourceOverwrite || overwrite;

        if (overwrite) {
          String value = currentState.metadata.get(key);
          checkState(null != value,
              "Metadata must contain a value for key %s if key is present", key);

          int intValue = Integer.parseInt(value);
          checkState(intValue > 0, "Metadata %s must be positive integer", key);
        }
      }

      if (hasVmResourceOverwrite) {
        checkState(allVmResourceOverwrite,
            "Management VM resource overwrite values must be specified together (CPU, memory, disk)");
      }
    }
  }

  private void validateManagementMetadata(State currentState, String key) {
    checkState(currentState.metadata.containsKey(key), String.format(
        "Metadata must contain key %s if the host is tagged as %s", key, UsageTag.MGMT.name()));
    checkState(null != currentState.metadata.get(key), String.format(
        "Metadata must contain a value for key %s if the host is tagged as %s", key, UsageTag.MGMT.name()));
  }

  private void validatePatchState(State startState, State patchState) {
    checkNotNull(patchState, "patch can not be null");
    ValidationUtils.validatePatch(startState, patchState);
  }

  /**
   * This class defines the document state associated with a single
   * {@link HostService} instance.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_AVAILABILITY_ZONE = "availabilityZone";
    public static final String FIELD_NAME_HOST_ADDRESS = "hostAddress";
    public static final String FIELD_NAME_REPORTED_DATASTORES = "reportedDatastores";
    public static final String FIELD_NAME_REPORTED_NETWORKS = "reportedNetworks";
    public static final String FIELD_NAME_SCHEDULING_CONSTANT = "schedulingConstant";
    public static final String FIELD_NAME_USAGE_TAGS = "usageTags";
    public static final String FIELD_NAME_REPORTED_IMAGE_DATASTORES = "reportedImageDatastores";


    public static final String USAGE_TAGS_KEY =
        QueryTask.QuerySpecification.buildCollectionItemName(FIELD_NAME_USAGE_TAGS);

    public static final String METADATA_KEY_NAME_MANAGEMENT_DATASTORE = "MANAGEMENT_DATASTORE";
    public static final String METADATA_KEY_NAME_ALLOWED_DATASTORES = "ALLOWED_DATASTORES";
    public static final String METADATA_KEY_NAME_ALLOWED_NETWORKS = "ALLOWED_NETWORKS";
    public static final String METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER = "MANAGEMENT_NETWORK_DNS_SERVER";
    public static final String METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY = "MANAGEMENT_NETWORK_GATEWAY";
    public static final String METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP = "MANAGEMENT_NETWORK_IP";
    public static final String METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK = "MANAGEMENT_NETWORK_NETMASK";
    public static final String METADATA_KEY_NAME_MANAGEMENT_PORTGROUP = "MANAGEMENT_PORTGROUP";
    public static final String METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE =
        "MANAGEMENT_VM_CPU_COUNT_OVERWRITE";
    public static final String METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE =
        "MANAGEMENT_VM_MEMORY_GB_OVERWRITE";
    public static final String METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE =
        "MANAGEMENT_VM_DISK_GB_OVERWRITE";
    public static final String METADATA_KEY_NAME_ALLOWED_SERVICES = "METADATA_KEY_NAME_ALLOWED_SERVICES";

    /**
     * This metadata should only be used for testing purpose. In the DeploymentWorkflowServiceTest, we need
     * to use test host's port number instead of the default because the test hosts are hosted on the same machine
     * and hence cannot share the same default port number. We implemented a test hook in {@link VmService} entity,
     * but since DeploymentWorkflowService automatically creates the VmService entities, we cannot set that test hook
     * directly. So we use this metadata as an additional test hook, and we will assign the test port number to the
     * VmService entity in {@link com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecTaskService}
     */
    public static final String METADATA_KEY_NAME_DEPLOYER_DCP_PORT = "DEPLOYER_DCP_PORT";

    /**
     * This value represents the state of the host.
     */
    @NotNull
    public HostState state;

    /**
     * This value represents the IP of the host.
     */
    @NotNull
    @Immutable
    public String hostAddress;

    /**
     * This value represents the port of the agent.
     */
    @NotNull
    @Immutable
    @DefaultInteger(8835)
    public Integer agentPort;

    /**
     * This value represents the user name to use when authenticating to the
     * host.
     */
    @NotNull
    @Immutable
    public String userName;

    /**
     * This value represents the password to use when authenticating to the
     * host.
     */
    @NotNull
    @Immutable
    public String password;

    /**
     * This value represents the availability zone this host belongs to.
     */
    public String availabilityZone;

    /**
     * This value represents the total physical memory of the host.
     */
    @WriteOnce
    @Range(min = 1, max = Integer.MAX_VALUE)
    public Integer memoryMb;

    /**
     * This value represents the total number of cpu cores of the host.
     */
    @WriteOnce
    @Range(min = 1, max = Integer.MAX_VALUE)
    public Integer cpuCount;

    /**
     * This value represents the usage tag associated with the host.
     */
    @NotEmpty
    @Immutable
    public Set<String> usageTags;

    /**
     * This value represents the metadata of the host.
     */
    @Immutable
    public Map<String, String> metadata;

    /**
     * This value represents the ESX version of the host.
     */
    public String esxVersion;

    /**
     * This value represents the state of the agent and is toggled by the chairman.
     */
    public AgentState agentState;

    /**
     * This value represents the mapping between the name (a.k.a mounting point) and the service link
     * of the datastore entity.
     */
    public Map<String, String> datastoreServiceLinks;

    /**
     * Currently unused. This is an optional field that defines the host group
     * affinity of this host. Chairman uses this information to group hosts in
     * the scheduler tree. If this field is not set, chairman puts the host into
     * a default host group.
     */
    public String hostGroup;

    /**
     * A set of datastore IDs reported by the host on registration. Chairman sets
     * this field when it receives a registration from the host.
     */
    public Set<String> reportedDatastores;

    /**
     * A set of networks reported by the host on registration. Chairman sets this
     * field when it receives a registration from the host. These are the networks
     * that are actually available on the host as opposed to the networks field
     * which represents the set of networks specified for this host in the dcmap.
     */
    public Set<String> reportedNetworks;

    /**
     * A set of image datastore IDs reported by the host on registration. Chairman
     * sets this field when it receives a registration from the host. This field is
     * a set and not a string since it's possible for the host to be provisioned
     * with multiple image datastores.
     */
    public Set<String> reportedImageDatastores;

    /**
     * This value represents a randomly assigned integer value. It is used to create
     * entropy when performing host constraint searches during scheduling.
     */
    @NotNull
    @Immutable
    public Long schedulingConstant;
  }
}
