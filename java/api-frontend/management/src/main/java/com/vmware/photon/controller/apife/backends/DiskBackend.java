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

import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;

import com.google.common.base.Optional;

/**
 * The Disk Backend Interface.
 */
public interface DiskBackend {

  PersistentDisk toApiRepresentation(String id) throws ExternalException;

  ResourceList<PersistentDisk> filter(String projectId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException;

  boolean existsUsingFlavor(String flavorId) throws ExternalException;

  TaskEntity prepareDiskCreate(String projectId, DiskCreateSpec spec) throws ExternalException;

  TaskEntity prepareDiskDelete(String diskId) throws ExternalException;

  BaseDiskEntity create(String projectId, AttachedDiskCreateSpec spec) throws ExternalException;

  void tombstone(String kind, String diskId) throws ExternalException;

  void updateState(BaseDiskEntity disk, DiskState state) throws DiskNotFoundException;

  void updateState(BaseDiskEntity disk, DiskState state, String agent, String datastore) throws DiskNotFoundException;

  BaseDiskEntity find(String kind, String id) throws DiskNotFoundException;

  ResourceList<PersistentDisk> getDisksPage(String pageLink) throws ExternalException;

}
