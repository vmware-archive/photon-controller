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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.entities.AvailabilityZoneEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import com.google.common.base.Optional;

/**
 * Backend interface for availability zone related operations.
 */
public interface AvailabilityZoneBackend {
  TaskEntity createAvailabilityZone(AvailabilityZoneCreateSpec availabilityZone) throws ExternalException;

  AvailabilityZone getApiRepresentation(String id) throws ExternalException;

  ResourceList<AvailabilityZone> getListApiRepresentation(Optional<Integer> pageSize) throws ExternalException;

  AvailabilityZoneEntity getEntityById(String id) throws ExternalException;

  ResourceList<AvailabilityZoneEntity> getAll(Optional<Integer> pageSize) throws ExternalException;

  ResourceList<AvailabilityZone> getPage(String pageLink) throws ExternalException;

  TaskEntity prepareAvailabilityZoneDelete(String id) throws ExternalException;

  void tombstone(AvailabilityZoneEntity availabilityZone) throws ExternalException;
}
