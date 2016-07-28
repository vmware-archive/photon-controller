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

package com.vmware.photon.controller.api.frontend.auth.fetcher;

import com.vmware.photon.controller.api.frontend.auth.TransactionAuthorizationObject;
import com.vmware.photon.controller.api.frontend.backends.ProjectBackend;
import com.vmware.photon.controller.api.frontend.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.api.model.Project;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of object that retrieves the security groups for a project authorization
 * object.
 */
public class ProjectSecurityGroupFetcher implements SecurityGroupFetcher {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(ProjectSecurityGroupFetcher.class);

  /**
   * Storage access object for project resources.
   */
  ProjectBackend backend;

  @Inject
  public ProjectSecurityGroupFetcher(ProjectBackend backend) {
    this.backend = backend;
  }

  @Override
  public Set<String> fetchSecurityGroups(TransactionAuthorizationObject authorizationObject) {
    checkArgument(authorizationObject.getKind() == TransactionAuthorizationObject.Kind.PROJECT,
        "authorizationObject must be of 'kind' PROJECT.");

    Set<String> securityGroups = new HashSet<>();
    try {
      Project project = backend.getApiRepresentation(authorizationObject.getId());
      switch (authorizationObject.getStrategy()) {
        case SELF:
          securityGroups = getAllSecurityGroups(project);
          break;

        case PARENT:
          securityGroups = getInheritedSecurityGroups(project);
          break;

        default:
          throw new IllegalArgumentException("authorizationObject must have 'strategy' SELF or PARENT.");
      }
    } catch (ProjectNotFoundException ex) {
      logger.warn("invalid project id {}", authorizationObject.getId());
    } catch (Exception ex) {
      logger.warn("unexpected exception processing project {}", authorizationObject.getId());
    }

    return securityGroups;
  }

  private Set<String> getAllSecurityGroups(Project project) {
    return project.getSecurityGroups().stream()
        .map(g -> g.getName())
        .collect(Collectors.toSet());
  }

  private Set<String> getInheritedSecurityGroups(Project project) {
    return project.getSecurityGroups().stream()
        .filter(g -> g.isInherited())
        .map(g -> g.getName())
        .collect(Collectors.toSet());
  }
}
