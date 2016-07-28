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

import com.vmware.photon.controller.api.frontend.entities.AttachedDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.BaseDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;

import java.util.List;

/**
 * AttachedDiskBackend is interface for attached disk backend.
 */
public interface AttachedDiskBackend {

  void attachDisks(VmEntity vm, List<PersistentDiskEntity> disks);

  void deleteAttachedDisks(VmEntity vm, List<PersistentDiskEntity> disks) throws ExternalException;

  List<AttachedDiskEntity> createAttachedDisks(VmEntity vm, List<AttachedDiskCreateSpec> specs) throws
      ExternalException;

  void deleteAttachedDisk(String kind, String id) throws ExternalException;

  AttachedDiskEntity findAttachedDisk(BaseDiskEntity diskEntity);

  List<AttachedDiskEntity> findByVmId(String vmId);

  void deleteAttachedDiskById(String attachedDiskId);
}
