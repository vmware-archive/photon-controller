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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.NetworkEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PortGroupsAlreadyAddedToSubnetException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PortGroupsDoNotExistException;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Subnet;
import com.vmware.photon.controller.api.model.SubnetCreateSpec;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perform network related operations using Xenon based cloud store.
 */
@Singleton
public class NetworkXenonBackend implements NetworkBackend {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final Logger logger = LoggerFactory.getLogger(NetworkXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;

  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final HostBackend hostBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public NetworkXenonBackend(
      ApiFeXenonRestClient xenonClient,
      TaskBackend taskBackend,
      VmBackend vmBackend,
      HostBackend hostBackend,
      TombstoneBackend tombstoneBackend) {
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
    this.hostBackend = hostBackend;
    this.tombstoneBackend = tombstoneBackend;

    this.xenonClient = xenonClient;
    this.xenonClient.start();
  }

  @Override
  public TaskEntity createNetwork(SubnetCreateSpec network)
      throws ExternalException {
    checkPortGroupsExist(network.getPortGroups());
    checkPortGroupsNotAddedToAnySubnet(network.getPortGroups());

    NetworkService.State state = new NetworkService.State();
    state.name = network.getName();
    state.description = network.getDescription();
    state.portGroups = network.getPortGroups();
    state.state = SubnetState.READY;

    com.vmware.xenon.common.Operation result = xenonClient.post(NetworkServiceFactory.SELF_LINK, state);

    NetworkService.State createdState = result.getBody(NetworkService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setId(id);
    networkEntity.setDescription(createdState.description);
    networkEntity.setPortGroups(getPortGroupsJSONString(createdState.portGroups));
    return taskBackend.createCompletedTask(networkEntity, Operation.CREATE_NETWORK);
  }

  @Override
  public ResourceList<Subnet> filter(Optional<String> name, Optional<String> portGroup, Optional<Integer> pageSize) {
    ServiceDocumentQueryResult queryResult = filterServiceDocuments(name, portGroup, pageSize);

    return PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult,
        state -> toApiRepresentation(convertToEntity(state)));
  }

  @Override
  public NetworkEntity findById(String id) throws NetworkNotFoundException {
    return convertToEntity(getById(id));
  }

  @Override
  public void tombstone(NetworkEntity network) throws ExternalException {
    List<Vm> vmsOnNetwork = vmBackend.filterByNetwork(network.getId());
    if (!vmsOnNetwork.isEmpty()) {
      logger.info("There are {} VMs still on network {}", vmsOnNetwork.size(), network.getId());
      return;
    }

    xenonClient.delete(
        NetworkServiceFactory.SELF_LINK + "/" + network.getId(),
        new NetworkService.State());
    tombstoneBackend.create(Subnet.KIND, network.getId());
    logger.info("network {} tombstoned", network.getId());
  }

  @Override
  public Subnet toApiRepresentation(String id) throws NetworkNotFoundException {
    return toApiRepresentation(convertToEntity(getById(id)));
  }

  @Override
  public TaskEntity prepareNetworkDelete(String id) throws ExternalException {
    NetworkEntity network = convertToEntity(getById(id));
    if (SubnetState.PENDING_DELETE.equals(network.getState())) {
      throw new InvalidNetworkStateException(
          String.format("Invalid operation to delete network %s in state PENDING_DELETE", network.getId()));
    }

    NetworkService.State networkState = new NetworkService.State();
    networkState.state = SubnetState.PENDING_DELETE;
    networkState.deleteRequestTime = System.currentTimeMillis();
    this.patchNetworkService(id, networkState);
    this.tombstone(network);

    return taskBackend.createCompletedTask(network, Operation.DELETE_NETWORK);
  }

  @Override
  public TaskEntity updatePortGroups(String id, List<String> portGroups)
      throws ExternalException {
    NetworkService.State network = getById(id);
    checkPortGroupsExist(portGroups);
    checkPortGroupsNotAddedToAnySubnet(getPortGroupDelta(network, portGroups));

    NetworkService.State networkStateUpdate = new NetworkService.State();
    networkStateUpdate.portGroups = portGroups;
    patchNetworkService(id, networkStateUpdate);

    TaskEntity task = taskBackend.createCompletedTask(convertToEntity(network), Operation.SET_PORT_GROUPS);
    return task;
  }

  @Override
  public TaskEntity setDefault(String networkId) throws ExternalException {
    NetworkService.State currentDefaultNetwork = getDefaultNetwork();

    if (currentDefaultNetwork != null) {
      NetworkService.State currentDefaultNetworkPatch = new NetworkService.State();
      currentDefaultNetworkPatch.isDefault = false;

      try {
        xenonClient.patch(currentDefaultNetwork.documentSelfLink,
            currentDefaultNetworkPatch);
      } catch (DocumentNotFoundException ex) {
        throw new NetworkNotFoundException(
            "Failed to patch current default network " + currentDefaultNetwork.documentSelfLink);
      }
    }

    NetworkService.State newDefaultNetwork = getById(networkId);
    NetworkService.State newDefaultNetworkPatch = new NetworkService.State();
    newDefaultNetworkPatch.isDefault = true;
    try {
      newDefaultNetwork = xenonClient.patch(newDefaultNetwork.documentSelfLink,
          newDefaultNetworkPatch).getBody(NetworkService.State.class);
    } catch (DocumentNotFoundException ex) {
      throw new NetworkNotFoundException(
          "Failed to patch new default network " + newDefaultNetwork.documentSelfLink);
    }

    return taskBackend.createCompletedTask(convertToEntity(newDefaultNetwork), Operation.SET_DEFAULT_NETWORK);
  }

  @Override
  public NetworkEntity getDefault() throws NetworkNotFoundException {
    NetworkService.State networkState = getDefaultNetwork();
    if (networkState == null) {
      throw new NetworkNotFoundException("default (physical)");
    }

    return convertToEntity(networkState);
  }

  @Override
  public ResourceList<Subnet> getPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult,
        state -> toApiRepresentation(convertToEntity(state)));
  }

  private void checkPortGroupsExist(List<String> portGroups)
      throws PortGroupsDoNotExistException {
    List<String> missingPortGroups = new ArrayList<>();

    for (String portGroup : portGroups) {
      ResourceList<Host> hosts = this.hostBackend.filterByPortGroup(
          portGroup, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      if (hosts.getItems().isEmpty()) {
        missingPortGroups.add(portGroup);
      }
    }

    if (!missingPortGroups.isEmpty()) {
      throw new PortGroupsDoNotExistException(missingPortGroups);
    }
  }

  private void checkPortGroupsNotAddedToAnySubnet(List<String> portGroups)
      throws PortGroupsAlreadyAddedToSubnetException {
    Map<String, Subnet> violations = new HashMap<>();
    for (String portGroup : portGroups) {
      ResourceList<Subnet> subnets = filter(Optional.<String>absent(), Optional.of(portGroup),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      if (!subnets.getItems().isEmpty()) {
        violations.put(portGroup, subnets.getItems().get(0));
      }
    }

    if (!violations.isEmpty()) {
      throw new PortGroupsAlreadyAddedToSubnetException(violations);
    }
  }

  private List<String> getPortGroupDelta(NetworkService.State networkState, List<String> portGroups) {
    List<String> delta = new ArrayList<>();
    for (String portGroup : portGroups) {
      if (networkState.portGroups.contains(portGroup)) {
        continue;
      }

      delta.add(portGroup);
    }

    return delta;
  }

  private void patchNetworkService(String id, NetworkService.State networkState)
      throws NetworkNotFoundException {
    try {
      xenonClient.patch(
          NetworkServiceFactory.SELF_LINK + "/" + id,
          networkState);
    } catch (DocumentNotFoundException e) {
      throw new NetworkNotFoundException(id);
    }
  }

  private String getPortGroupsJSONString(List<String> portGroups) {
    try {
      return objectMapper.writeValueAsString(portGroups);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error serializing portGroups", e);
    }
  }

  private List<String> getPortGroupsFromJSONString(String portGroupsJSONString) {
    try {
      return objectMapper.readValue(portGroupsJSONString,
          new TypeReference<List<String>>() {
          });
    } catch (IOException ex) {
      throw new IllegalArgumentException("Error deserializing portGroups " + portGroupsJSONString, ex);
    }
  }

  private NetworkService.State getById(String id) throws NetworkNotFoundException {
    try {
      com.vmware.xenon.common.Operation result = xenonClient.get(NetworkServiceFactory.SELF_LINK + "/" + id);
      return result.getBody(NetworkService.State.class);
    } catch (DocumentNotFoundException exception) {
      throw new NetworkNotFoundException(id);
    }
  }

  private NetworkService.State getDefaultNetwork() {
    ImmutableMap.Builder<String, String> termsBuilder = new ImmutableBiMap.Builder<>();
    termsBuilder.put("isDefault", Boolean.TRUE.toString());

    List<NetworkService.State> defaultNetworks =
        xenonClient.queryDocuments(NetworkService.State.class, termsBuilder.build());

    if (defaultNetworks != null && !defaultNetworks.isEmpty()) {
      return defaultNetworks.iterator().next();
    } else {
      return null;
    }
  }

  private NetworkEntity convertToEntity(NetworkService.State network) {
    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setName(network.name);
    networkEntity.setDescription(network.description);
    networkEntity.setState(network.state);
    networkEntity.setPortGroups(getPortGroupsJSONString(network.portGroups));
    networkEntity.setIsDefault(network.isDefault);
    networkEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(network.documentSelfLink));

    return networkEntity;
  }

  private Subnet toApiRepresentation(NetworkEntity entity) {
    Subnet subnet = new Subnet();
    subnet.setId(entity.getId());
    subnet.setName(entity.getName());
    subnet.setState(entity.getState());
    subnet.setDescription(entity.getDescription());
    subnet.setPortGroups(getPortGroupsFromJSONString(entity.getPortGroups()));
    subnet.setIsDefault(entity.getIsDefault());

    return subnet;
  }

  private ServiceDocumentQueryResult filterServiceDocuments(Optional<String> name, Optional<String> portGroup,
                                                            Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (portGroup.isPresent()) {
      termsBuilder.put(NetworkService.State.FIELD_PORT_GROUPS_QUERY_KEY, portGroup.get().toString());
    }

    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering networks using terms {}", terms);

    return xenonClient.queryDocuments(NetworkService.State.class, terms, pageSize, true);
  }

}
