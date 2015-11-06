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
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.db.dao.NetworkDao;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NetworkBackend is performing network operations (create etc.) as instructed by API calls.
 */
@Singleton
public class NetworkSqlBackend implements NetworkBackend {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final NetworkDao networkDao;
  private final TombstoneBackend tombstoneBackend;
  private final TaskBackend taskBackend;

  @Inject
  public NetworkSqlBackend(NetworkDao networkDao,
                           TombstoneBackend tombstoneBackend,
                           TaskBackend taskBackend) {
    this.networkDao = networkDao;
    this.tombstoneBackend = tombstoneBackend;
    this.taskBackend = taskBackend;
  }

  @Transactional
  public TaskEntity createNetwork(NetworkCreateSpec network) throws PortGroupsAlreadyAddedToNetworkException {
    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setName(network.getName());
    networkEntity.setDescription(network.getDescription());
    networkEntity.setState(NetworkState.READY);
    networkEntity.setPortGroups(getPortGroupsJSONString(checkPortGroupsNotAddedToAnyNetwork(network.getPortGroups())));
    networkDao.create(networkEntity);

    return taskBackend.createCompletedTask(networkEntity, Operation.CREATE_NETWORK);
  }

  @Transactional
  public List<Network> filter(Optional<String> name, Optional<String> portGroup) {
    List<NetworkEntity> networks;
    if (name.isPresent()) {
      networks = networkDao.listByName(name.get());
    } else {
      networks = networkDao.listAll();
    }

    List<Network> result = new ArrayList<>(networks.size());
    for (NetworkEntity network : networks) {
      if (!portGroup.isPresent() ||
          getPortGroupsFromJSONString(network.getPortGroups()).contains(portGroup.get())) {
        result.add(toApiRepresentation(network));
      }
    }

    return result;
  }

  @Transactional
  public NetworkEntity findById(String id) throws NetworkNotFoundException {
    Optional<NetworkEntity> network = networkDao.findById(id);

    if (network.isPresent()) {
      return network.get();
    }

    throw new NetworkNotFoundException(id);
  }

  @Transactional
  public void tombstone(NetworkEntity network) throws ExternalException {
    throw new NotImplementedException();
  }

  @Transactional
  public Network toApiRepresentation(String id) throws NetworkNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public TaskEntity prepareNetworkDelete(String id) throws ExternalException {
    throw new NotImplementedException();
  }

  private String getPortGroupsJSONString(List<String> portGroups) {
    try {
      return objectMapper.writeValueAsString(portGroups);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error serializing portGroups", e);
    }
  }

  @Override
  public TaskEntity updatePortGroups(String id, List<String> portGroups)
      throws ExternalException {
    throw new NotImplementedException();
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

  private Network toApiRepresentation(NetworkEntity networkEntity) {
    Network network = new Network();

    network.setId(networkEntity.getId());
    network.setName(networkEntity.getName());
    network.setDescription(networkEntity.getDescription());
    network.setState(networkEntity.getState());
    network.setPortGroups(getPortGroupsFromJSONString(networkEntity.getPortGroups()));

    return network;
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
}
