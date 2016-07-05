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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Subnet;
import com.vmware.photon.controller.api.SubnetCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupRepeatedInMultipleNetworksException;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;

import com.google.common.base.Optional;

import java.util.List;

/**
 * Perform network related operations.
 */
public interface NetworkBackend {

  TaskEntity createNetwork(SubnetCreateSpec network) throws ExternalException;

  ResourceList<Subnet> filter(Optional<String> name, Optional<String> portGroup, Optional<Integer> pageSize);

  NetworkService.State filterNetworkByPortGroup(Optional<String> portGroup)
          throws PortGroupRepeatedInMultipleNetworksException;

  ResourceList<Subnet> getPage(String pageLink) throws ExternalException;

  NetworkEntity findById(String id) throws NetworkNotFoundException;

  void tombstone(NetworkEntity network) throws ExternalException;

  Subnet toApiRepresentation(String id) throws NetworkNotFoundException;

  TaskEntity prepareNetworkDelete(String id) throws ExternalException;

  TaskEntity updatePortGroups(String id, List<String> portGroups) throws ExternalException;

  NetworkEntity getDefault() throws NetworkNotFoundException;

  TaskEntity setDefault(String id) throws ExternalException;
}
