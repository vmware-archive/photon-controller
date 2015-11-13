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

import com.vmware.photon.controller.api.AuthInfo;
import com.vmware.photon.controller.api.ClusterConfiguration;
import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.db.dao.DeploymentDao;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentAlreadyExistException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Deployment SQL backend.
 */
@Singleton
public class DeploymentSqlBackend implements DeploymentBackend {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentSqlBackend.class);

  private final DeploymentDao deploymentDao;
  private final TaskBackend taskBackend;
  private final EntityLockBackend entityLockBackend;
  private final TombstoneBackend tombstoneBackend;
  private final TenantBackend tenantBackend;

  @Inject
  public DeploymentSqlBackend(DeploymentDao deploymentDao,
                              TaskBackend taskBackend,
                              EntityLockBackend entityLockBackend,
                              TombstoneBackend tombstoneBackend,
                              TenantBackend tenantBackend) {
    this.deploymentDao = deploymentDao;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.tenantBackend = tenantBackend;
  }

  @Override
  @Transactional
  public TaskEntity prepareCreateDeployment(DeploymentCreateSpec spec) throws ExternalException {
    if (!getAll().isEmpty()) {
      throw new DeploymentAlreadyExistException();
    }
    DeploymentEntity deploymentEntity = create(spec);
    logger.info("created deployment {}", deploymentEntity);

    TaskEntity taskEntity = taskBackend.createCompletedTask(deploymentEntity, Operation.CREATE_DEPLOYMENT);
    logger.info("created task {}", taskEntity);
    return taskEntity;
  }

  @Override
  @Transactional
  public TaskEntity prepareDeleteDeployment(String id) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(id);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.DELETE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Delete deployment {}", deploymentEntity);
    tombstone(deploymentEntity);
    TaskEntity taskEntity = this.taskBackend.createCompletedTask(deploymentEntity, Operation.DELETE_DEPLOYMENT);
    logger.info("created task {}", taskEntity);
    return taskEntity;
  }

  @Override
  @Transactional
  public TaskEntity prepareDeploy(String deploymentId) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(deploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.PERFORM_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    TaskEntity taskEntity = createDeployTask(deploymentEntity);
    logger.info("created task {}", taskEntity);

    return taskEntity;
  }

  @Override
  @Transactional
  public TaskEntity prepareDestroy(String deploymentId) throws ExternalException {
    DeploymentEntity deploymentEntity = findById(deploymentId);
    EntityStateValidator.validateOperationState(deploymentEntity, deploymentEntity.getState(),
        Operation.PERFORM_DELETE_DEPLOYMENT, DeploymentState.OPERATION_PREREQ_STATE);

    logger.info("Destroy deployment {}", deploymentEntity);
    TaskEntity taskEntity = destroyTask(deploymentEntity);
    logger.info("created task {}", taskEntity);
    return taskEntity;
  }

  @Override
  @Transactional
  public TaskEntity pauseSystem(String deploymentId) throws ExternalException {
    DeploymentEntity deployment = findById(deploymentId);
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deployment, Operation.PAUSE_SYSTEM);
    this.taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.PAUSE_SYSTEM);

    return taskEntity;
  }

  @Transactional
  public TaskEntity resumeSystem(String deploymentId) throws ExternalException {
    DeploymentEntity deployment = findById(deploymentId);
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deployment, Operation.RESUME_SYSTEM);
    this.taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESUME_SYSTEM);

    return taskEntity;
  }

  @Override
  public TaskEntity prepareInitializeMigrateDeployment(String sourceLoadbalancerAddress,
                                                       String destinationDeploymentId) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  public TaskEntity prepareFinalizeMigrateDeployment(String sourceLoadbalancerAddress, String destinationDeploymentId)
      throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  @Transactional
  public TaskEntity updateSecurityGroups(String id, List<String> securityGroups) throws ExternalException {

    DeploymentEntity deploymentEntity = findById(id);

    logger.info("Updating the security groups of deployment {} to {}", id, securityGroups.toString());

    boolean authEnabled = deploymentEntity.getAuthEnabled();
    if (!authEnabled) {
      throw new InvalidAuthConfigException("Auth is not enabled, and security groups cannot be set.");
    }

    List<String> currSecurityGroups = deploymentEntity.getOauthSecurityGroups();
    currSecurityGroups.clear();
    currSecurityGroups.addAll(securityGroups);

    deploymentDao.update(deploymentEntity);

    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity,
        Operation.UPDATE_DEPLOYMENT_SECURITY_GROUPS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, deploymentEntity,
        Operation.PUSH_DEPLOYMENT_SECURITY_GROUPS);

    List<TenantEntity> tenantEntities = tenantBackend.getAllTenantEntities();
    if (tenantEntities != null && !tenantEntities.isEmpty()) {
      List<BaseEntity> tenantEntitiesToBePushed = new ArrayList<BaseEntity>();
      tenantEntitiesToBePushed.addAll(tenantEntities);
      taskBackend.getStepBackend().createQueuedStep(taskEntity, tenantEntitiesToBePushed,
          Operation.PUSH_TENANT_SECURITY_GROUPS);
    }

    logger.info("Created task: {}", taskEntity);
    return taskEntity;
  }

  @Override
  @Transactional
  public Deployment toApiRepresentation(String id) throws DeploymentNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Override
  @Transactional
  public Deployment toApiRepresentation(DeploymentEntity deploymentEntity) {
    Deployment deployment = new Deployment();

    deployment.setId(deploymentEntity.getId());
    deployment.setState(deploymentEntity.getState());
    deployment.setImageDatastore(deploymentEntity.getImageDatastore());
    deployment.setSyslogEndpoint(deploymentEntity.getSyslogEndpoint());
    deployment.setNtpEndpoint(deploymentEntity.getNtpEndpoint());
    deployment.setUseImageDatastoreForVms(deploymentEntity.getUseImageDatastoreForVms());
    AuthInfo authInfo = new AuthInfo();
    authInfo.setEnabled(deploymentEntity.getAuthEnabled());
    authInfo.setEndpoint(deploymentEntity.getOauthEndpoint());
    authInfo.setTenant(deploymentEntity.getOauthTenant());
    authInfo.setSecurityGroups(deploymentEntity.getOauthSecurityGroups());
    deployment.setAuth(authInfo);
    deployment.setLoadBalancerEnabled(deploymentEntity.getLoadBalancerEnabled());

    return deployment;
  }

  @Override
  @Transactional
  public void updateState(DeploymentEntity entity, DeploymentState state) {
    entity.setState(state);
    this.deploymentDao.update(entity);
  }

  @Override
  @Transactional
  public void tombstone(DeploymentEntity deploymentEntity) {
    tombstoneBackend.create(deploymentEntity.getKind(), deploymentEntity.getId());
    deploymentDao.delete(deploymentEntity);
  }

  @Override
  @Transactional
  public ClusterConfiguration configureCluster(ClusterConfigurationSpec spec) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  @Transactional
  public TaskEntity deleteClusterConfiguration(ClusterType clusterType) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  @Transactional
  public List<ClusterConfiguration> getClusterConfigurations() throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  @Transactional
  public List<Deployment> getAll() {
    return toApiRepresentation(deploymentDao.listAll());
  }

  @Override
  @Transactional
  public DeploymentEntity findById(String id) throws DeploymentNotFoundException {
    Optional<DeploymentEntity> deployment = deploymentDao.findById(id);

    if (!deployment.isPresent()) {
      throw new DeploymentNotFoundException(id);
    }
    return deployment.get();
  }

  private List<Deployment> toApiRepresentation(List<DeploymentEntity> deploymentEntityList) {
    List<Deployment> deploymentList = new ArrayList<>();

    for (DeploymentEntity entity : deploymentEntityList) {
      deploymentList.add(toApiRepresentation(entity));
    }

    return deploymentList;
  }

  private DeploymentEntity create(DeploymentCreateSpec spec) {
    DeploymentEntity entity = new DeploymentEntity();

    entity.setState(DeploymentState.NOT_DEPLOYED);
    entity.setImageDatastore(spec.getImageDatastore());
    entity.setUseImageDatastoreForVms(spec.isUseImageDatastoreForVms());
    entity.setSyslogEndpoint(spec.getSyslogEndpoint());
    entity.setNtpEndpoint(spec.getNtpEndpoint());
    entity.setAuthEnabled(spec.getAuth().getEnabled());
    entity.setOauthEndpoint(spec.getAuth().getEndpoint());
    entity.setOauthPort(spec.getAuth().getPort());
    entity.setOauthTenant(spec.getAuth().getTenant());
    entity.setOauthUsername(spec.getAuth().getUsername());
    entity.setOauthPassword(spec.getAuth().getPassword());
    entity.setOauthSecurityGroups(spec.getAuth().getSecurityGroups());
    entity.setLoadBalancerEnabled(spec.getLoadBalancerEnabled());

    return deploymentDao.create(entity);
  }

  private TaskEntity createDeployTask(DeploymentEntity deploymentEntity) throws ExternalException {
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity, Operation.PERFORM_DEPLOYMENT);

    // create the steps
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PREPARE_DEPLOYMENT);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_DEPLOYMENT);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.BUILD_DEPLOYMENT_PLAN);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.BUILD_RUNTIME_CONFIGURATION);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CONTROL_PLANE_HOSTS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CONTROL_PLANE_VMS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CLOUD_HOSTS);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PROVISION_CLUSTER_MANAGER);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.MIGRATE_DEPLOYMENT_DATA);

    // lock the entity
    this.entityLockBackend.setStepLock(deploymentEntity, taskEntity.getSteps().get(0));
    return taskEntity;
  }

  private TaskEntity destroyTask(DeploymentEntity deploymentEntity) throws ExternalException {
    TaskEntity taskEntity = this.taskBackend.createQueuedTask(deploymentEntity, Operation.DESTROY_DEPLOYMENT);

    // create the steps
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.SCHEDULE_DELETE_DEPLOYMENT);
    this.taskBackend.getStepBackend().createQueuedStep(
        taskEntity, deploymentEntity, Operation.PERFORM_DELETE_DEPLOYMENT);

    // lock the entity
    this.entityLockBackend.setStepLock(deploymentEntity, taskEntity.getSteps().get(0));
    return taskEntity;
  }
}
