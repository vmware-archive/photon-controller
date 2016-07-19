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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.SecurityGroupsAlreadyInheritedException;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;

import com.google.common.base.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * Step command to push the security groups of the deployment to its projects.
 */
public class DeploymentPushSecurityGroupsStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentPushSecurityGroupsStepCmd.class);

  private final TenantBackend tenantBackend;

  public DeploymentPushSecurityGroupsStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                                             StepEntity stepEntity, TenantBackend tenantBackend) {

    super(taskCommand, stepBackend, stepEntity);

    this.tenantBackend = tenantBackend;
  }

  @Override
  protected void execute() throws ExternalException {
    List<DeploymentEntity> deploymentEntities = step.getTransientResourceEntities(null);
    checkArgument(deploymentEntities.size() == 1);

    DeploymentEntity deploymentEntity = deploymentEntities.get(0);

    logger.info("Propagating the security groups of deployment {}", deploymentEntity.getId());

    ResourceList<Tenant> tenants = tenantBackend.filter(Optional.absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    ResourceList<Tenant> currentPage;

    List<String> deploymentSecurityGroups = deploymentEntity.getOauthSecurityGroups();

    do {
      for (Tenant tenant : tenants.getItems()) {
        logger.info("Updating the security groups of tenant {} using the ones from deployment {}",
            tenant.getId(), deploymentEntity.getId());

        List<SecurityGroup> currSecurityGroups = tenant.getSecurityGroups();
        Pair<List<SecurityGroup>, List<String>> result =
            SecurityGroupUtils.mergeParentSecurityGroups(currSecurityGroups, deploymentSecurityGroups);

        tenantBackend.setSecurityGroups(tenant.getId(), result.getLeft());

        // Needs to change the securityGroupsAlreadyInhertiedException
        // Currently it does not identify which entity these SGs are for.
        if (result.getRight() != null && !result.getRight().isEmpty()) {
          step.addWarning(new SecurityGroupsAlreadyInheritedException(result.getRight()));
        }
      }

      currentPage = tenants;

      if (tenants.getNextPageLink() != null && !tenants.getNextPageLink().isEmpty()) {
        tenants = tenantBackend.getPage(tenants.getNextPageLink());
      }

    } while (currentPage.getNextPageLink() != null && !currentPage.getNextPageLink().isEmpty());

  }

  @Override
  protected void cleanup() {

  }
}
