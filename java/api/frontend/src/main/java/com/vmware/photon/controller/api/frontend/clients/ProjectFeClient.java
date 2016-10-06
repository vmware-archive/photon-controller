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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.backends.ProjectBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Frontend client for project used by {@link ProjectResource}.
 */
@Singleton
public class ProjectFeClient {

  private final ProjectBackend projectBackend;
  private final TaskBackend taskBackend;
  private final AuthConfig authConfig;

  @Inject
  public ProjectFeClient(ProjectBackend projectBackend, TaskBackend taskBackend, AuthConfig authConfig) {
    this.projectBackend = projectBackend;
    this.taskBackend = taskBackend;
    this.authConfig = authConfig;
  }

  public Project get(String id) throws ExternalException {
    return projectBackend.getApiRepresentation(id);
  }

  public ResourceList<Project> find(String tenantId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    return projectBackend.filter(tenantId, name, pageSize);
  }

  public ResourceList<Project> find(String tenantId, Optional<String> name, Optional<Integer> pageSize,
                                    List<String> tokenGroups, String defaultAdminGroup)
      throws ExternalException {
    if (authConfig.isAuthEnabled()) {
      if (tokenGroups == null || !tokenGroups.contains(defaultAdminGroup)) {
        return projectBackend.filter(tenantId, name, pageSize, tokenGroups);
      }
    }

    return projectBackend.filter(tenantId, name, pageSize);
  }

  public Task create(String tenantId, ProjectCreateSpec project) throws ExternalException {
    TaskEntity taskEntity = projectBackend.createProject(tenantId, project);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = projectBackend.deleteProject(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task setSecurityGroups(String projectId, List<String> securityGroups) throws ExternalException {
    TaskEntity taskEntity = projectBackend.setSecurityGroups(projectId, securityGroups);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public ResourceList<Project> getProjectsPage(String pageLink) throws ExternalException {
    return projectBackend.getProjectsPage(pageLink);
  }
}
