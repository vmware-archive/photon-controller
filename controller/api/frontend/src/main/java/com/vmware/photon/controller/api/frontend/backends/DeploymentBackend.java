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

import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentDeployOperation;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.model.InitializeMigrationOperation;
import com.vmware.photon.controller.api.model.NsxConfigurationSpec;
import com.vmware.photon.controller.api.model.ServiceConfiguration;
import com.vmware.photon.controller.api.model.ServiceConfigurationSpec;
import com.vmware.photon.controller.api.model.ServiceType;

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

  TaskEntity prepareInitializeMigrateDeployment(InitializeMigrationOperation initializeMigrationOperation,
                                                String destinationDeploymentId) throws ExternalException;

  TaskEntity prepareFinalizeMigrateDeployment(FinalizeMigrationOperation finalizeMigrationOperation,
                                              String destinationDeploymentId) throws ExternalException;

  Deployment toApiRepresentation(String id) throws DeploymentNotFoundException;

  Deployment toApiRepresentation(DeploymentEntity deploymentEntity);

  void updateState(DeploymentEntity entity, DeploymentState state) throws DeploymentNotFoundException;

  void tombstone(DeploymentEntity deploymentEntity);

  List<Deployment> getAll();

  TaskEntity configureService(ServiceConfigurationSpec spec) throws ExternalException;

  TaskEntity deleteServiceConfiguration(ServiceType serviceType) throws ExternalException;

  List<ServiceConfiguration> getServiceConfigurations() throws ExternalException;

  TaskEntity configureNsx(NsxConfigurationSpec spec) throws ExternalException;

  DeploymentEntity findById(String id) throws DeploymentNotFoundException;

  TaskEntity prepareUpdateImageDatastores(String id, List<String> imageDatastores) throws ExternalException;

  TaskEntity prepareSyncHostsConfig(String id) throws ExternalException;
}
