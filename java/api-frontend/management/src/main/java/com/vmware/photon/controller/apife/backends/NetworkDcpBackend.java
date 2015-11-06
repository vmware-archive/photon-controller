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
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
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
 * Perform network related operations using dcp based cloud store.
 */
@Singleton
public class NetworkDcpBackend implements NetworkBackend {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final Logger logger = LoggerFactory.getLogger(NetworkDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;

  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public NetworkDcpBackend(
      ApiFeDcpRestClient dcpClient,
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

    com.vmware.dcp.common.Operation result = dcpClient.postAndWait(NetworkServiceFactory.SELF_LINK, state);

    NetworkService.State createdState = result.getBody(NetworkService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);

    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setId(id);
    networkEntity.setDescription(createdState.description);
    networkEntity.setPortGroups(getPortGroupsJSONString(createdState.portGroups));
    return taskBackend.createCompletedTask(networkEntity, Operation.CREATE_NETWORK);
  }

  @Override
  public List<Network> filter(Optional<String> name, Optional<String> portGroup) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (portGroup.isPresent()) {
      termsBuilder.put(NetworkService.PORT_GROUPS_KEY, portGroup.get().toString());
    }

    List<Network> networks = new ArrayList<>();
    ImmutableMap<String, String> terms = termsBuilder.build();
    logger.info("Filtering Port Groups using terms {}", terms);
    List<NetworkService.State> documents =
        dcpClient.queryDocuments(NetworkService.State.class, terms);
    for (NetworkService.State state : documents) {
      networks.add(toApiRepresentation(convertToEntity(state)));
    }

    return networks;
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

    dcpClient.deleteAndWait(
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

  private List<String> checkPortGroupsNotAddedToAnyNetwork(List<String> portGroups)
      throws PortGroupsAlreadyAddedToNetworkException {
    Map<String, Network> violations = new HashMap<>();
    for (String portGroup : portGroups) {
      List<Network> networks = filter(Optional.<String>absent(), Optional.of(portGroup));
      if (!networks.isEmpty()) {
        violations.put(portGroup, networks.get(0));
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
      dcpClient.patchAndWait(
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
      com.vmware.dcp.common.Operation result = dcpClient.getAndWait(NetworkServiceFactory.SELF_LINK + "/" + id);
      return result.getBody(NetworkService.State.class);
    } catch (DocumentNotFoundException exception) {
      throw new NetworkNotFoundException(id);
    }
  }

  private NetworkEntity convertToEntity(NetworkService.State network) {
    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setName(network.name);
    networkEntity.setDescription(network.description);
    networkEntity.setState(network.state);
    networkEntity.setPortGroups(getPortGroupsJSONString(network.portGroups));
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

    return network;
  }
}
