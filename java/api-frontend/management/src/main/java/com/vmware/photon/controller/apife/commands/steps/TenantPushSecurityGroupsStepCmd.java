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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;

import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StepCommand for pushing the security groups of a tenant to its projects.
 */
public class TenantPushSecurityGroupsStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(TenantPushSecurityGroupsStepCmd.class);

  private final TenantBackend tenantBackend;
  private final ProjectBackend projectBackend;

  public TenantPushSecurityGroupsStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                                         StepEntity stepEntity, TenantBackend tenantBackend,
                                         ProjectBackend projectBackend) {

    super(taskCommand, stepBackend, stepEntity);

    this.tenantBackend = tenantBackend;
    this.projectBackend = projectBackend;
  }

  @Override
  protected void execute() throws ExternalException {
    List<TenantEntity> tenantList = step.getTransientResourceEntities(null);
    checkArgument(tenantList.size() > 0);

    for (TenantEntity tenantEntity : tenantList) {
      logger.info("Propagating the security groups of tenant {}", tenantEntity.getId());

      // Since this step might be called by deployment when propagating the security groups,
      // we need to refresh the tenantEntity to make sure it has the latest security groups.
      tenantEntity = tenantBackend.findById(tenantEntity.getId());

      List<Project> projects = new ArrayList<>();
      ResourceList<Project> resourceList = projectBackend.filter(tenantEntity.getId(), Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      projects.addAll(resourceList.getItems());

      while (StringUtils.isNotBlank(resourceList.getNextPageLink())) {
        resourceList = projectBackend.getProjectsPage(resourceList.getNextPageLink());
        projects.addAll(resourceList.getItems());
      }

      List<String> tenantSecurityGroups = tenantEntity.getSecurityGroups().stream()
          .map(g -> g.getName()).collect(Collectors.toList());

      for (Project project : projects) {
        logger.info("Updating the security groups of project {} using the ones from tenant {}",
            project.getId(), tenantEntity.getId());

        List<SecurityGroup> currSecurityGroups = project.getSecurityGroups();
        Pair<List<SecurityGroup>, List<String>> result =
            SecurityGroupUtils.mergeParentSecurityGroups(currSecurityGroups, tenantSecurityGroups);

        projectBackend.replaceSecurityGroups(project.getId(), result.getLeft());

        if (result.getRight() != null && !result.getRight().isEmpty()) {
          step.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
        }
      }
    }
  }

  @Override
  protected void cleanup() {
  }
}
