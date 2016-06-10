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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupRepeatedInMultipleNetworksException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perform network related operations using dcp based cloud store.
 */
@Singleton
public class NetworkDcpBackend implements NetworkBackend {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final Logger logger = LoggerFactory.getLogger(NetworkDcpBackend.class);

  private final ApiFeXenonRestClient dcpClient;

  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public NetworkDcpBackend(
      ApiFeXenonRestClient dcpClient,
      TaskBackend taskBackend,
      VmBackend vmBackend,
      TombstoneBackend tombstoneBackend) {
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
    this.tombstoneBackend = tombstoneBackend;

    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public TaskEntity createNetwork(NetworkCreateSpec network)
      throws PortGroupsAlreadyAddedToNetworkException {
    NetworkService.State state = new NetworkService.State();
    state.name = network.getName();
    state.description = network.getDescription();
    state.portGroups = checkPortGroupsNotAddedToAnyNetwork(network.getPortGroups());
    state.state = NetworkState.READY;

    com.vmware.xenon.common.Operation result = dcpClient.post(NetworkServiceFactory.SELF_LINK, state);

    NetworkService.State createdState = result.getBody(NetworkService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setId(id);
    networkEntity.setDescription(createdState.description);
    networkEntity.setPortGroups(getPortGroupsJSONString(createdState.portGroups));
    return taskBackend.createCompletedTask(networkEntity, Operation.CREATE_NETWORK);
  }

  @Override
  public ResourceList<Network> filter(Optional<String> name, Optional<String> portGroup, Optional<Integer> pageSize) {
    ServiceDocumentQueryResult queryResult = filterServiceDocuments(name, portGroup, pageSize);

    return PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult,
        state -> toApiRepresentation(convertToEntity(state)));
  }

  @Override
  public NetworkService.State filterNetworkByPortGroup(Optional<String> portGroup)
          throws PortGroupRepeatedInMultipleNetworksException {
    ServiceDocumentQueryResult queryResult = filterServiceDocuments(Optional.absent(), portGroup, Optional.absent());

    ResourceList<NetworkService.State> networksList =
            PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult);

    if (networksList == null || networksList.getItems() == null || networksList.getItems().size() == 0) {
      return null;
    } else if (networksList.getItems().size() > 1) {
      Map<String, List<NetworkService.State>> violations = new HashMap<>();
      violations.put(portGroup.toString(), networksList.getItems());
      throw new PortGroupRepeatedInMultipleNetworksException(violations);
    } else {
      return networksList.getItems().get(0);
    }
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

    dcpClient.delete(
        NetworkServiceFactory.SELF_LINK + "/" + network.getId(),
        new NetworkService.State());
    tombstoneBackend.create(Network.KIND, network.getId());
    logger.info("network {} tombstoned", network.getId());
  }

  @Override
  public Network toApiRepresentation(String id) throws NetworkNotFoundException {
    return toApiRepresentation(convertToEntity(getById(id)));
  }

  @Override
  public TaskEntity prepareNetworkDelete(String id) throws ExternalException {
    NetworkEntity network = convertToEntity(getById(id));
    if (NetworkState.PENDING_DELETE.equals(network.getState())) {
      throw new InvalidNetworkStateException(
          String.format("Invalid operation to delete network %s in state PENDING_DELETE", network.getId()));
    }

    NetworkService.State networkState = new NetworkService.State();
    networkState.state = NetworkState.PENDING_DELETE;
    networkState.deleteRequestTime = System.currentTimeMillis();
    this.patchNetworkService(id, networkState);
    this.tombstone(network);

    return taskBackend.createCompletedTask(network, Operation.DELETE_NETWORK);
  }

  @Override
  public TaskEntity updatePortGroups(String id, List<String> portGroups)
      throws NetworkNotFoundException {
    NetworkEntity network = convertToEntity(getById(id));

    NetworkService.State networkState = new NetworkService.State();
    networkState.portGroups = portGroups;
    patchNetworkService(id, networkState);

    TaskEntity task = taskBackend.createCompletedTask(network, Operation.SET_PORT_GROUPS);
    return task;
  }

  @Override
  public TaskEntity setDefault(String networkId) throws ExternalException {
    NetworkService.State currentDefaultNetwork = getDefaultNetwork();

    if (currentDefaultNetwork != null) {
      NetworkService.State currentDefaultNetworkPatch = new NetworkService.State();
      currentDefaultNetworkPatch.isDefault = false;

      try {
        dcpClient.patch(currentDefaultNetwork.documentSelfLink,
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
      newDefaultNetwork = dcpClient.patch(newDefaultNetwork.documentSelfLink,
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
      throw new NetworkNotFoundException("No default network is found");
    }

    return convertToEntity(networkState);
  }

  @Override
  public ResourceList<Network> getPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = dcpClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult,
        state -> toApiRepresentation(convertToEntity(state)));
  }

  private List<String> checkPortGroupsNotAddedToAnyNetwork(List<String> portGroups)
      throws PortGroupsAlreadyAddedToNetworkException {
    Map<String, Network> violations = new HashMap<>();
    for (String portGroup : portGroups) {
      ResourceList<Network> networks = filter(Optional.<String>absent(), Optional.of(portGroup),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      if (!networks.getItems().isEmpty()) {
        violations.put(portGroup, networks.getItems().get(0));
      }
    }

    if (!violations.isEmpty()) {
      throw new PortGroupsAlreadyAddedToNetworkException(violations);
    }
    return portGroups;
  }

  private void patchNetworkService(String id, NetworkService.State networkState)
      throws NetworkNotFoundException {
    try {
      dcpClient.patch(
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
      com.vmware.xenon.common.Operation result = dcpClient.get(NetworkServiceFactory.SELF_LINK + "/" + id);
      return result.getBody(NetworkService.State.class);
    } catch (DocumentNotFoundException exception) {
      throw new NetworkNotFoundException(id);
    }
  }

  private NetworkService.State getDefaultNetwork() {
    ImmutableMap.Builder<String, String> termsBuilder = new ImmutableBiMap.Builder<>();
    termsBuilder.put("isDefault", Boolean.TRUE.toString());

    List<NetworkService.State> defaultNetworks =
        dcpClient.queryDocuments(NetworkService.State.class, termsBuilder.build());

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

  private Network toApiRepresentation(NetworkEntity entity) {
    Network network = new Network();
    network.setId(entity.getId());
    network.setName(entity.getName());
    network.setState(entity.getState());
    network.setDescription(entity.getDescription());
    network.setPortGroups(getPortGroupsFromJSONString(entity.getPortGroups()));
    network.setIsDefault(entity.getIsDefault());

    return network;
  }

  private ServiceDocumentQueryResult filterServiceDocuments(Optional<String> name, Optional<String> portGroup,
                                                     Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (portGroup.isPresent()) {
      termsBuilder.put(NetworkService.PORT_GROUPS_KEY, portGroup.get().toString());
    }

    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering Port Groups using terms {}", terms);

    return dcpClient.queryDocuments(NetworkService.State.class, terms, pageSize, true);
  }

}
