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

import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.HostNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostCreateSpec;
import com.vmware.photon.controller.api.model.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.UsageTag;

import com.google.common.base.Optional;


/**
 * The Host Backend Interface.
 */

public interface HostBackend {

  TaskEntity prepareHostCreate(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException;

  TaskEntity prepareHostDelete(String id) throws ExternalException;

  HostEntity findById(String id) throws HostNotFoundException;

  ResourceList<Host> listAll(Optional<Integer> pageSize);

  ResourceList<Host> getHostsPage(String pageLink) throws PageExpiredException;

  int getNumberHosts();

  ResourceList<Host> filterByUsage(UsageTag usageTag, Optional<Integer> pageSize);

  ResourceList<Host> filterByAddress(String address, Optional<Integer> pageSize);

  ResourceList<Host> filterByPortGroup(String portGroup, Optional<Integer> pageSize);

  Host toApiRepresentation(String id) throws HostNotFoundException;

  void updateState(HostEntity entity, HostState state) throws HostNotFoundException;

  void updateAvailabilityZone(HostEntity entity) throws HostNotFoundException;

  void tombstone(HostEntity hostEntity);

  TaskEntity setAvailabilityZone(String hostId, HostSetAvailabilityZoneOperation hostSetAvailabilityZoneOperation)
      throws ExternalException;

  TaskEntity resume(String hostId) throws ExternalException;

  TaskEntity suspend(String hostId) throws ExternalException;

  TaskEntity enterMaintenance(String hostId) throws ExternalException;

  TaskEntity exitMaintenance(String hostId) throws ExternalException;

  ResourceList<Host> filterByState(HostState hostState, Optional<Integer> pageSize);
}
