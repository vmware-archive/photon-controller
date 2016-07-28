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

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.exceptions.external.TenantNotFoundException;

import com.google.common.base.Optional;

import java.util.List;

/**
 * Backend interface for tenant related operations.
 */
public interface TenantBackend {

  ResourceList<Tenant> filter(Optional<String> name, Optional<Integer> pageSize);

  List<TenantEntity> getAllTenantEntities();

  Tenant getApiRepresentation(String id) throws TenantNotFoundException;

  TaskEntity createTenant(TenantCreateSpec tenant) throws ExternalException;

  TaskEntity deleteTenant(String tenantId) throws ExternalException;

  TenantEntity findById(String id) throws TenantNotFoundException;

  TaskEntity prepareSetSecurityGroups(String tenantId, List<String> securityGroups) throws ExternalException;

  void setSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException;

  ResourceList<Tenant> getPage(String pageLink) throws PageExpiredException;
}
