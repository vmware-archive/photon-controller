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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.task.DatastoreDeleteFactoryService;
import com.vmware.photon.controller.cloudstore.xenon.task.DatastoreDeleteService;
import com.vmware.photon.controller.cloudstore.xenon.upgrade.HostTransformationService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientProvider;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotEmpty;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.Range;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceMaintenanceRequest;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon micro-service which provides a plain data object
 * representing an ESX host.
 */
public class HostService extends StatefulService {

  private final Random random = new Random();

  /**
   * All the default values specified below are just good starting points. We might
   * need to adjust them after scale testing.
   *
   * DEFAULT_MAINTENANCE_INTERVAL = DEFAULT_MAX_PING_WAIT_TIME + MAX_TIME_TO_PING + BUFFER = 50 + 5 + 5 = 60s
   *
   * At DEFAULT_MAX_PING_WAIT_TIME of 50 seconds, we will send 20 pings every second if we have 1000 hosts and 200
   * pings every second if we have 10,000 hosts. The actual ping, get and post are all asynchronous. So we should be
   * able to scale to 1000 hosts without issues and 10,000 hosts depending on the load on cloud store by other services.
   */

  /**
   * The default maintenance interval controls how often we poll the agent for its
   * state (60 seconds).
   */
  public static final long DEFAULT_MAINTENANCE_INTERVAL_MILLIS = 60 * 1000;

  /**
   * This value represents the upper bound of the wait time in milliseconds for a host
   * service instance to ping the agent within its polling interval (10 seconds).
   */
  public static final int DEFAULT_MAX_PING_WAIT_TIME_MILLIS = 50 * 1000;

  /**
   * This value represents the time we have to wait for all the datastore documents to
   * get updated before completing the maintenance.
   */
  public static final int UPDATE_TIMEOUT_SECONDS = 5;

  /**
   * The scheduling constant is a random number in (0, MAX_SCHEDULING_CONSTANT] assigned to each HostService.State. The
   * CloudStoreContraintChecker uses it to randomly select hosts.
   */
  public static final int MAX_SCHEDULING_CONSTANT = 10000;

  /**
   * This determines when the host datastores and networks should be checked and updated through
   * a polling interval (10 minutes). This is set to 10 minutes because the datastores and networks
   * attached to a host do not change very often. Also the agent detects the datastores attached to
   * it by periodically polling vsphere SDK every 10 minutes. So even if we set an interval less
   * than that we will not get any new data.
   */
  public static final long UPDATE_HOST_METADATA_INTERVAL = TimeUnit.MINUTES.toMillis(10);

  /**
   * This represents the soft state that maintains the last maintenance time of the host metadata (datastores and
   * networks) update.
   */
  private long lastHostMetadataUpdateTime;

  private static boolean inUnitTests = false;

  public HostService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    // this service cannot have ON_DEMAND_LOAD option set since it requires periodic maintenance
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(DEFAULT_MAINTENANCE_INTERVAL_MILLIS));
    this.lastHostMetadataUpdateTime = System.currentTimeMillis();
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);

      if (null == startState.schedulingConstant) {
        startState.schedulingConstant = (long) random.nextInt(MAX_SCHEDULING_CONSTANT);
      }

      validateState(startState);

      // set the maintenance interval to match the value in the state.
      this.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(startState.triggerIntervalMillis));
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

      boolean stateChangedToReady =
          startState.state != HostState.READY && patchState.state == HostState.READY;
      boolean agentStateChangedToActive =
          startState.agentState != AgentState.ACTIVE && patchState.agentState == AgentState.ACTIVE;
      boolean agentStateChangedToMissing =
          startState.agentState != AgentState.MISSING && patchState.agentState == AgentState.MISSING;

      String newEsxVersion = patchState.esxVersion;
      Set<String> previouslyReportedDatastores = startState.reportedDatastores;
      Set<String> newlyReportedDatastores = patchState.reportedDatastores;

      PatchUtils.patchState(startState, patchState);
      validateState(startState);

      patchOperation.complete();

      // If the host state changed to READY, the host service proactively calls the agent to get host configuration
      // and update the host document. This gets exercised during host provisioning and also when a host is moved
      // back to READY state from MAINTENANCE, NOT_PROVISIONED, etc.
      // If the agent state changed to ACTIVE from null or MISSING and the esxVersion is null (to make sure that the
      // patch is not from a getHostConfig() call), then also the host service proactively calls the agent to get
      // host configuration and update the host document. This is needed to make sure that when an agent which was
      // temporarily unavailable came back online, we make sure that the host config has not changed.
      // Apart from these two cases, host configuration is updated as part of handleMaintenance of the Host Service
      // at periodic intervals.
      if (stateChangedToReady || (agentStateChangedToActive && newEsxVersion == null)) {
        getHostConfig(null, startState);
      }

      // If the agent state changed to MISSING, then the hosts' reported datastores may be eligible for deletion. So
      // we call the delete datastore task on all the datastores which were previously reported by this host. The
      // delete datastore task decides if the datastore needs to be deleted or not. To make sure that the deleted
      // documents are recreated when the agent becomes active, we get the host config everytime the agent becomes
      // active.
      // If not, get the list of datastores which became inactive since the last update and call the datastore delete
      // task on them as they might be eligible for deletion.
      if (agentStateChangedToMissing) {
        triggerDatastoreDeleteTasks(previouslyReportedDatastores);
      } else {
        if (previouslyReportedDatastores != null && newlyReportedDatastores != null) {
          Set<String> inactiveDatastoreIds = previouslyReportedDatastores.stream()
              .filter(id -> !newlyReportedDatastores.contains(id))
              .collect(Collectors.toSet());
          triggerDatastoreDeleteTasks(inactiveDatastoreIds);
        }
      }

    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * When we are running in the unit tests, we want to disable the host ping because it will always fail (we have fake
   * hosts) and mark the agentState as missing. This allows us to do so.
   *
   * @param inUnitTests
   */
  public static void setInUnitTests(boolean inUnitTests) {
    HostService.inUnitTests = inUnitTests;
  }

  /**
   * Handle periodic maintenance calls for all host service instances. We will be using this to ping all the hosts
   * periodically to check if they are alive.
   *
   * @param maintenance
   */
  @Override
  public void handleMaintenance(Operation maintenance) {
    if (HostService.inUnitTests) {
      return;
    }
    try {
      // Return if the maintenance call is not the periodically scheduled one
      ServiceMaintenanceRequest request = maintenance.getBody(ServiceMaintenanceRequest.class);
      if (!request.reasons.contains(ServiceMaintenanceRequest.MaintenanceReason.PERIODIC_SCHEDULE)) {
        maintenance.complete();
        return;
      }
      getHost().schedule(() -> {
        Operation getOperation = Operation.createGet(this, maintenance.getUri().getPath())
            .setCompletion((op, ex) -> {
              if (ex != null) {
                ServiceUtils.logWarning(this, "Get request failed on Host Service to ping agent " + ex.getMessage());
                maintenance.complete();
                return;
              }

              State hostState = op.getBody(State.class);
              // Retrieve host metadata if the interval has elapsed. Otherwise, just ping the
              // host to perform a health-check.
              if (System.currentTimeMillis() - lastHostMetadataUpdateTime >= UPDATE_HOST_METADATA_INTERVAL) {
                getHostConfig(maintenance, hostState);
              } else {
                pingHost(maintenance, hostState);
              }
            });
        sendRequest(getOperation);
      }, ThreadLocalRandom.current().nextInt(1, DEFAULT_MAX_PING_WAIT_TIME_MILLIS), TimeUnit.MILLISECONDS);
    } catch (Exception exception) {
      ServiceUtils.logWarning(this, "Handle maintenance failed " + exception.getMessage());
      maintenance.complete();
    }
  }

  /**
   * This method pings the agent on the host identified by the host document and updates the agent state in the host
   * document if it determines a change in agent state.
   * The thrift call to ping the agent is implemented as a runnable, which will be scheduled to run in the future.
   * The time after which it runs is determined by a random integer between 1 and maxPingWaitTimeMillis. This is needed
   * to achieve a randomized distribution of the polling task so that we reduce the number of concurrent connections.
   *
   * @param maintenance
   * @param hostState
   */
  private void pingHost(Operation maintenance, State hostState) {
    final Service service = this;
    try {
      AgentControlClient agentControlClient = ((AgentControlClientProvider) getHost()).getAgentControlClient();
      agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      agentControlClient.ping(new AsyncMethodCallback<AgentControl.AsyncClient.ping_call>() {
        @Override
        public void onComplete(AgentControl.AsyncClient.ping_call pingCall) {
          updateHostState(maintenance, hostState, AgentState.ACTIVE);
        }

        @Override
        public void onError(Exception e) {
          ServiceUtils.logInfo(service, "Failed to ping " + hostState.hostAddress + ", will be marked as missing:" +
              e.getMessage());
          updateHostState(maintenance, hostState, AgentState.MISSING);
        }
      });
    } catch (Exception ex) {
      ServiceUtils.logWarning(this, "Unexpected exception while pinging " + hostState.hostAddress + ", will be " +
          "marked as missing:" + ex.getMessage());
      updateHostState(maintenance, hostState, AgentState.MISSING);
    }
  }

  /**
   * This method gets the host config (datastores, networks, etc.) from agent.
   * It also sets the agentState to ACTIVE or MISSING, depending on the result
   * of the getHostConfig call. Because this updates agentState, handleMaintenance
   * does not call pingHost when host configuration is updated.
   *
   * The operation parameter is null when executed by handlePatch. But it
   * is set for handleMaintenance.
   */
  private void getHostConfig(Operation operation, State hostState) {
    try {
      final Service service = this;
      lastHostMetadataUpdateTime = System.currentTimeMillis();
      HostClient hostClient = ((HostClientProvider) getHost()).getHostClient();
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.getHostConfig(new AsyncMethodCallback<Host.AsyncClient.get_host_config_call>() {
        @Override
        public void onComplete(Host.AsyncClient.get_host_config_call getHostConfigCall) {
          try {
            GetConfigResponse response = getHostConfigCall.getResult();
            HostClient.ResponseValidator.checkGetConfigResponse(response);
            processHostConfig(operation, hostState, response.getHostConfig());
          } catch (Throwable t) {
            ServiceUtils.logWarning(service, "Failed to retrieve host config. Setting agentState to MISSING. " +
                "Exception:" + t.getMessage());
            updateHostState(operation, hostState, AgentState.MISSING);
          }
        }

        @Override
        public void onError(Exception e) {
          ServiceUtils.logWarning(service, "Failed to retrieve host config. Setting agentState to MISSING. " +
              "Exception:" + e.getMessage());
          updateHostState(operation, hostState, AgentState.MISSING);
        }
      });
    } catch (Exception e) {
      ServiceUtils.logWarning(this, "Failed to retrieve host config. Setting agentState to MISSING. " +
          "Exception:" + e.getMessage());
      updateHostState(operation, hostState, AgentState.MISSING);
    }
  }

  /**
   * This method updates the host state with the received host config.
   *
   * @param operation
   * @param hostState
   * @param hostConfig
   */
  private void processHostConfig(Operation operation, State hostState, HostConfig hostConfig) {
    List<Datastore> datastores = hostConfig.getDatastores();
    List<Network> networks = hostConfig.getNetworks();
    Set<String> imageDatastoreIds = hostConfig.getImage_datastore_ids();

    try {
      HostService.State patchState = new HostService.State();
      patchState.agentState = AgentState.ACTIVE;
      patchState.reportedDatastores = new HashSet<>();
      patchState.datastoreServiceLinks = new HashMap<>();
      patchState.reportedImageDatastores = new HashSet<>();
      patchState.reportedNetworks = new HashSet<>();

      if (datastores != null && datastores.size() > 0) {
        for (Datastore datastore : datastores) {
          patchState.reportedDatastores.add(datastore.getId());
          patchState.datastoreServiceLinks
              .put(datastore.getName(), DatastoreServiceFactory.getDocumentLink(datastore.getId()));
        }
      }

      if (networks != null && networks.size() > 0) {
        for (Network network : networks) {
          if (network.getTypes() != null && network.getTypes().contains(NetworkType.VM)) {
            // TEMPORARY WORKAROUND: Currently the portgroup document doesn't
            // contain the network type information, so we are filtering them
            // here so that cloudstore has VM networks for the scheduler
            patchState.reportedNetworks.add(network.getId());
          }
        }
      }

      if (imageDatastoreIds != null && imageDatastoreIds.size() > 0) {
        for (String datastoreId : imageDatastoreIds) {
          patchState.reportedImageDatastores.add(datastoreId);
        }
      }

      if (hostConfig.isSetCpu_count()) {
        patchState.cpuCount = hostConfig.getCpu_count();
      }
      if (hostConfig.isSetEsx_version()) {
        patchState.esxVersion = hostConfig.getEsx_version();
      }
      if (hostConfig.isSetMemory_mb()) {
        patchState.memoryMb = hostConfig.getMemory_mb();
      }

      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable ex) {
      ServiceUtils.logWarning(this, "Failed to update " + hostState.hostAddress + " with state: " +
          Utils.toJson(true, false, hostState) + " " + ex.getMessage());
    }

    // Update datastore state
    setDatastoreState(operation, datastores, imageDatastoreIds);
  }

  /**
   * This method creates or updates datastore state that was sent as a part of host config.
   *
   * @param datastores
   * @param imageDatastores
   * @param operation
   */
  private void setDatastoreState(Operation operation, List<Datastore> datastores, Set<String> imageDatastores) {
    if (datastores != null) {
      // Create datastore documents.
      final CountDownLatch done = new CountDownLatch(datastores.size());
      for (Datastore datastore : datastores) {
        DatastoreService.State datastoreState = new DatastoreService.State();
        datastoreState.documentSelfLink = datastore.getId();
        datastoreState.id = datastore.getId();
        datastoreState.name = datastore.getName();
        datastoreState.type = datastore.getType().toString();
        datastoreState.tags = datastore.getTags();
        datastoreState.isImageDatastore = imageDatastores.contains(datastore.getId());

        try {
          Operation post = Operation
              .createPost(UriUtils.buildUri(getHost(), DatastoreServiceFactory.SELF_LINK))
              .setBody(datastoreState)
              .setCompletion((op, ex) -> {
                if (ex != null) {
                  ServiceUtils.logWarning(this, "Set datastore state failed " + ex.getMessage());
                }
                done.countDown();
              });
          sendRequest(post);
        } catch (Throwable t) {
          ServiceUtils.logWarning(this, "Set datastore state failed " + t.getMessage());
          done.countDown();
        }
      }
      try {
        done.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        logWarning("Got interrupted waiting for datastore update operations to complete");
      }
    }
    if (operation != null) {
      operation.complete();
    }
  }

  /**
   * Patch the Host Service document with the agent state if it has changed.
   *
   * @param operation
   * @param hostState
   * @param agentState
   */
  private void updateHostState(Operation operation, State hostState, AgentState agentState) {
    if (hostState.agentState != agentState) {
      State patchState = new State();
      patchState.agentState = agentState;
      ServiceUtils.logInfo(this, "Agent state for host " + hostState.hostAddress + " changed from " +
          hostState.agentState + " " + "-> " + agentState);
      TaskUtils.sendSelfPatch(this, patchState);
    }
    if (operation != null) {
      operation.complete();
    }
  }

  /**
   * Trigger DatastoreDeleteService for each of the inactive datastores. The DatastoreDeleteService checks if a
   * datastore is eligible for deletion based on the hosts which are referring to it and deletes them if they are
   * eligible.
   *
   * @param datastoreIds
   */
  private void triggerDatastoreDeleteTasks(Set<String> datastoreIds) {
    if (datastoreIds != null) {
      for (String id : datastoreIds) {
        DatastoreDeleteService.State startState = new DatastoreDeleteService.State();
        startState.parentServiceLink = getSelfLink();
        startState.datastoreId = id;

        sendRequest(Operation
            .createPost(this, DatastoreDeleteFactoryService.SELF_LINK)
            .setBody(startState));
      }
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument template = super.getDocumentTemplate();
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_REPORTED_DATASTORES);
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_REPORTED_IMAGE_DATASTORES);
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
          State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_MB_OVERWRITE,
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
  @MigrateDuringUpgrade(transformationServicePath = HostTransformationService.SELF_LINK,
      sourceFactoryServicePath = HostServiceFactory.SELF_LINK,
      destinationFactoryServicePath = HostServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = HostServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_STATE = "state";
    public static final String FIELD_NAME_AGENT_STATE = "agentState";
    public static final String FIELD_NAME_AVAILABILITY_ZONE_ID = "availabilityZoneId";
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
    public static final String METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_MB_OVERWRITE =
        "MANAGEMENT_VM_MEMORY_MB_OVERWRITE";
    public static final String METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE =
        "MANAGEMENT_VM_DISK_GB_OVERWRITE";
    public static final String METADATA_KEY_NAME_ALLOWED_SERVICES = "ALLOWED_SERVICES";

    /**
     * This metadata should only be used for testing purpose. In the DeploymentWorkflowServiceTest, we need
     * to use test host's port number instead of the default because the test hosts are hosted on the same machine
     * and hence cannot share the same default port number. We implemented a test hook in {@link VmService} entity,
     * but since DeploymentWorkflowService automatically creates the VmService entities, we cannot set that test hook
     * directly. So we use this metadata as an additional test hook, and we will assign the test port number to the
     * VmService entity in CreateVmSpecTaskService.
     */
    public static final String METADATA_KEY_NAME_DEPLOYER_XENON_PORT = "DEPLOYER_XENON_PORT";

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
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
    public String userName;

    /**
     * This value represents the password to use when authenticating to the
     * host.
     */
    @NotNull
    @Immutable
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
    public String password;

    /**
     * This value represents the availability zone this host belongs to.
     */
    public String availabilityZoneId;

    /**
     * This value represents the total physical memory of the host.
     */
    @Range(min = 1, max = Integer.MAX_VALUE)
    public Integer memoryMb;

    /**
     * This value represents the total number of cpu cores of the host.
     */
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

    /**
     * The time interval to trigger the service. (This value will be used to set the
     * maintenance interval on the service.)
     */
    @DefaultLong(value = DEFAULT_MAINTENANCE_INTERVAL_MILLIS)
    @Positive
    public Long triggerIntervalMillis;

    /**
     * This value represents the ID of the host being registered as a fabric node in
     * NSX environment.
     */
    @WriteOnce
    public String nsxFabricNodeId;

    /**
     * This value represents the ID of the host being registerd as a transport node in
     * NSX environment.
     */
    @WriteOnce
    public String nsxTransportNodeId;
  }
}
