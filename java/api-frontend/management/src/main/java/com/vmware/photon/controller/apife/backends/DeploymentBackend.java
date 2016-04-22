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

import com.vmware.photon.controller.api.ClusterConfiguration;
import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DeploymentDeployOperation;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;

import java.util.List;

/**
 * Deployment backend.
 */
public interface DeploymentBackend {

  TaskEntity prepareCreateDeployment(DeploymentCreateSpec spec) throws ExternalException;

  TaskEntity prepareDeploy(String deploymentId, DeploymentDeployOperation config) throws ExternalException;

  TaskEntity prepareDeleteDeployment(String id) throws ExternalException;

  TaskEntity prepareDestroy(String deploymentId) throws ExternalException;

  TaskEntity updateSecurityGroups(String id, List<String> securityGroups) throws ExternalException;

  TaskEntity pauseSystem(String deploymentId) throws ExternalException;

  TaskEntity pauseBackgroundTasks(String deploymentId) throws ExternalException;

  TaskEntity resumeSystem(String deploymentId) throws ExternalException;

  TaskEntity prepareInitializeMigrateDeployment(String sourceLoadbalancerAddress, String destinationDeploymentId)
      throws ExternalException;

  TaskEntity prepareFinalizeMigrateDeployment(String sourceLoadbalancerAddress, String destinationDeploymentId)
      throws ExternalException;

  Deployment toApiRepresentation(String id) throws DeploymentNotFoundException;

  Deployment toApiRepresentation(DeploymentEntity deploymentEntity);

  void updateState(DeploymentEntity entity, DeploymentState state) throws DeploymentNotFoundException;

  void tombstone(DeploymentEntity deploymentEntity);

  List<Deployment> getAll();

  ClusterConfiguration configureCluster(ClusterConfigurationSpec spec) throws ExternalException;

  TaskEntity deleteClusterConfiguration(ClusterType clusterType) throws ExternalException;

  List<ClusterConfiguration> getClusterConfigurations() throws ExternalException;

  DeploymentEntity findById(String id) throws DeploymentNotFoundException;

  TaskEntity prepareUpdateImageDatastores(String id, List<String> imageDatastores) throws ExternalException;
}
