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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.utils.SecurityGroupUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * StepCommand for setting the security groups of a tenant.
 */
public class TenantSetSecurityGroupsStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(TenantSetSecurityGroupsStepCmd.class);

  private final TenantBackend tenantBackend;

  public TenantSetSecurityGroupsStepCmd(TaskCommand taskCommand, StepBackend stepBackend,
                                        StepEntity stepEntity, TenantBackend tenantBackend) {

    super(taskCommand, stepBackend, stepEntity);
    this.tenantBackend = tenantBackend;
  }

  @Override
  protected void execute() throws ExternalException {
    List<TenantEntity> tenantList = step.getTransientResourceEntities(null);
    checkArgument(tenantList.size() == 1);

    TenantEntity tenantEntity = tenantList.get(0);

    logger.info("Updating the security groups of tenant {}", tenantEntity.getId());

    tenantBackend.setSecurityGroups(tenantEntity.getId(),
        SecurityGroupUtils.toApiRepresentation(tenantEntity.getSecurityGroups()));
  }

  @Override
  protected void cleanup() {
  }
}
