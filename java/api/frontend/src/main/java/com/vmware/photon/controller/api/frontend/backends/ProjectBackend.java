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

import com.vmware.photon.controller.api.frontend.entities.ProjectEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;

import com.google.common.base.Optional;

import java.util.List;

/**
 * Backend interface for project related operations.
 */
public interface ProjectBackend {
  ResourceList<Project> filter(String tenantId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException;

  Project getApiRepresentation(String id) throws ExternalException;

  TaskEntity createProject(String tenantId, ProjectCreateSpec project) throws ExternalException;

  TaskEntity deleteProject(String projectId) throws ExternalException;

  ProjectEntity findById(String id) throws ProjectNotFoundException;

  TaskEntity setSecurityGroups(String projectId, List<String> securityGroups) throws ExternalException;

  void replaceSecurityGroups(String id, List<SecurityGroup> securityGroups) throws ExternalException;

  ResourceList<Project> getProjectsPage(String pageLink) throws ExternalException;

  int getNumberProjects(Optional<String> tenantId);

}
