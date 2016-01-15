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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentFailedException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand that updates ImageConfig data based on deployment information.
 */
public class DeploymentImageConfigUpdateStepCmd extends StepCommand {
  protected static final String USE_FOR_IMAGE_UPLOAD_KEY = "useForImageUpload";
  private static final Logger logger = LoggerFactory.getLogger(DeploymentImageConfigUpdateStepCmd.class);
  private final DeploymentBackend deploymentBackend;
  private final HostBackend hostBackend;
  private final ImageConfig config;
  private DeploymentEntity entity;

  public DeploymentImageConfigUpdateStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                            DeploymentBackend deploymentBackend, HostBackend hostBackend,
                                            ImageConfig config) {
    super(taskCommand, stepBackend, step);
    this.deploymentBackend = deploymentBackend;
    this.hostBackend = hostBackend;
    this.config = config;
  }

  @Override
  protected void execute() throws ApiFeException, RpcException, InterruptedException {
    if (!config.useEsxStore()) {
      // we are running with fake agent so nothing to do.
      return;
    }

    // get the deployment entity
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);
    this.entity = deploymentEntityList.get(0);

    // check and update data store information
    try {
      this.config.getDatastore();
    } catch (NullPointerException e) {
      this.updateDataStore();
    }

    // check and update host information
    try {
      this.config.getEndpoint();
    } catch (NullPointerException e) {
      this.updateHost();
    }
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.entity != null) {
      logger.info("Deployment create failed, mark entity {} state as ERROR", this.entity.getId());
      try {
        this.deploymentBackend.updateState(this.entity, DeploymentState.ERROR);
      } catch (DeploymentNotFoundException e) {
        logger.warn("Could not find deployment to mark as error, DeploymentId=" + e.getId(), e);
      }
    }
  }

  @VisibleForTesting
  protected void setEntity(DeploymentEntity entity) {
    this.entity = entity;
  }

  private void updateDataStore() {
    this.config.setDatastore(this.entity.getImageDatastores().iterator().next());
  }

  private void updateHost() throws ApiFeException {
    ResourceList<Host> hostList = hostBackend.filterByUsage(UsageTag.MGMT,
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    if (hostList.getItems().size() == 0) {
      throw new DeploymentFailedException(this.entity.getId(), "No management hosts found.");
    }

    Host firstHost = hostList.getItems().get(0);

    ResourceList<Host> currentPage;

    do {
      // try to find the host marked for
      for (Host host : hostList.getItems()) {
        if (host.getMetadata().containsKey(USE_FOR_IMAGE_UPLOAD_KEY)) {
          logger.info("using host {} to upload images", host);
          this.config.setEndpoint(formatEndpoint(host));
          return;
        }
      }

      currentPage = hostList;

      if (hostList.getNextPageLink() != null && !hostList.getNextPageLink().isEmpty()) {
        hostList = hostBackend.getHostsPage(hostList.getNextPageLink());
      }

    } while (currentPage.getNextPageLink() != null && !currentPage.getNextPageLink().isEmpty());

    // pick the first host since none was explicitly specified
    logger.info("using host {} to upload images", firstHost);
    this.config.setEndpoint(formatEndpoint(firstHost));
  }

  private String formatEndpoint(Host host) {
    return String.format("http://%s", host.getAddress());
  }
}
