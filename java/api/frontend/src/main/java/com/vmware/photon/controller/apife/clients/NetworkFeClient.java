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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Subnet;
import com.vmware.photon.controller.api.model.SubnetCreateSpec;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Frontend client for task used by {@link NetworksResource}.
 */
@Singleton
public class NetworkFeClient {

  private final NetworkBackend networkBackend;
  private final TaskBackend taskBackend;

  @Inject
  public NetworkFeClient(NetworkBackend networkBackend, TaskBackend taskBackend) {
    this.networkBackend = networkBackend;
    this.taskBackend = taskBackend;
  }

  public Task create(SubnetCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = networkBackend.createNetwork(spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Subnet get(String id) throws NetworkNotFoundException {
    return networkBackend.toApiRepresentation(id);
  }

  public ResourceList<Subnet> find(Optional<String> name, Optional<Integer> pageSize) {
    return networkBackend.filter(name, Optional.<String>absent(), pageSize);
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = networkBackend.prepareNetworkDelete(id);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public Task setPortGroups(String networkId, List<String> portGroups) throws ExternalException {
    TaskEntity taskEntity = networkBackend.updatePortGroups(networkId, portGroups);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public Task setDefault(String networkId) throws ExternalException {
    TaskEntity taskEntity = networkBackend.setDefault(networkId);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public ResourceList<Subnet> getPage(String pageLink) throws ExternalException {
    return networkBackend.getPage(pageLink);
  }
}
