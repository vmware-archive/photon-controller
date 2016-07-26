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

import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.PageExpiredException;

import com.google.common.base.Optional;

/**
 * Backend interface for flavor related operations.
 */
public interface FlavorBackend {
  TaskEntity createFlavor(FlavorCreateSpec flavor) throws ExternalException;

  Flavor getApiRepresentation(String id) throws ExternalException;

  TaskEntity prepareFlavorDelete(String id) throws ExternalException;

  ResourceList<FlavorEntity> getAll(Optional<Integer> pageSize) throws ExternalException;

  ResourceList<Flavor> filter(Optional<String> name, Optional<String> kind, Optional<Integer> pageSize)
          throws ExternalException;

  ResourceList<Flavor> getFlavorsPage(String pageLink) throws PageExpiredException;

  FlavorEntity getEntityByNameAndKind(String name, String kind) throws ExternalException;

  FlavorEntity getEntityById(String id) throws ExternalException;

  void tombstone(FlavorEntity flavor) throws ExternalException;
}
