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

package com.vmware.photon.controller.apife.commands.tasks;

import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.BaseCommand;
import com.vmware.photon.controller.apife.commands.steps.StepCommand;
import com.vmware.photon.controller.apife.commands.steps.StepCommandFactory;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.TaskNotCompletedException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.RootSchedulerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.scheduler.gen.FindResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

/**
 * This class extends BaseCommand and represents a long running activity. Its corresponding DB/API
 * object is a Task.
 */
public class TaskCommand extends BaseCommand {

  private static final Logger logger = LoggerFactory.getLogger(TaskCommand.class);

  @Inject
  protected TaskBackend taskBackend;
  protected List<StepEntity> steps;
  @Inject
  private StepCommandFactory stepCommandFactory;
  private TaskEntity task;
  private Resource resource;
  private String reservation;
  private ApiFeDcpRestClient dcpClient;
  private RootSchedulerClient rootSchedulerClient;
  private HostClient hostClient;
  private HousekeeperClient housekeeperClient;
  private DeployerClient deployerClient;
  private EntityLockBackend entityLockBackend;

  @Inject
  public TaskCommand(ApiFeDcpRestClient dcpClient,
                     RootSchedulerClient rootSchedulerClient,
                     HostClient hostClient,
                     HousekeeperClient housekeeperClient,
                     DeployerClient deployerClient,
                     EntityLockBackend entityLockBackend,
                     @Assisted TaskEntity task) {
    super(task.getId());
    this.task = checkNotNull(task);
    this.dcpClient = dcpClient;
    this.rootSchedulerClient = checkNotNull(rootSchedulerClient);
    this.hostClient = checkNotNull(hostClient);
    this.housekeeperClient = checkNotNull(housekeeperClient);
    this.deployerClient = deployerClient;
    this.entityLockBackend = entityLockBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    steps = getTask().getSteps();
    for (StepEntity step : steps) {
      if (!step.getState().equals(StepEntity.State.QUEUED) || step.isDisabled()) {
        logger.info("Skip running step {}", step);
        continue;
      }

      StepCommand cmd = stepCommandFactory.createCommand(this, step);
      cmd.run();

      if (step.getState() != StepEntity.State.COMPLETED) {
        throw new TaskNotCompletedException(step);
      }
    }
  }

  @Override
  protected void markAsStarted() throws TaskNotFoundException, ConcurrentTaskException {
    taskBackend.markTaskAsStarted(task);
    for (String toBeLockedEntityId : task.getToBeLockedEntityIds()) {
      entityLockBackend.setTaskLock(toBeLockedEntityId, task);
    }
  }

  @Override
  protected void markAsDone() throws TaskNotFoundException {
    //clear locks as first thing in this method so that some other failure does not preempt it leaving dangling lock.
    entityLockBackend.clearTaskLocks(task);
    for (StepEntity step : getTask().getSteps()) {
      if (!step.getState().equals(StepEntity.State.COMPLETED)) {
        logger.info("Step {} is incomplete. Skip marking task {} as done", step, task.getId());
        return;
      }
    }
    taskBackend.markTaskAsDone(task);
  }

  /**
   * @param t The throwable exception for why this task failed.
   */
  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    //clear locks as first thing in this method so that some other failure does not preempt it leaving dangling lock.
    entityLockBackend.clearTaskLocks(task);
    logger.error("Task {} failed", getActivityId(), t);
    taskBackend.markTaskAsFailed(task);
  }

  @Override
  protected void cleanup() {
    entityLockBackend.clearTaskLocks(task);
    getHostClient().close();
  }

  @VisibleForTesting
  protected void setTaskBackend(TaskBackend taskBackend) {
    this.taskBackend = taskBackend;
  }

  @VisibleForTesting
  protected void setStepCommandFactory(StepCommandFactory stepCommandFactory) {
    this.stepCommandFactory = stepCommandFactory;
  }

  public void markAllStepsAsFailed(Throwable t) throws TaskNotFoundException {
    //clear locks as first thing in this method so that some other failure does not preempt it leaving dangling lock.
    entityLockBackend.clearTaskLocks(task);
    logger.error("Task {} failed", getActivityId(), t);
    taskBackend.markAllStepsAsFailed(task, t);
  }

  public TaskEntity getTask() {
    return checkNotNull(task);
  }

  public Resource getResource() {
    return checkNotNull(resource);
  }

  public void setResource(Resource resource) {
    this.resource = checkNotNull(resource);
  }

  public String getReservation() {
    return checkNotNull(reservation);
  }

  public void setReservation(String reservation) {
    this.reservation = checkNotNull(reservation);
  }

  public HostClient getHostClient() {
    return checkNotNull(hostClient);
  }

  public RootSchedulerClient getRootSchedulerClient() {
    return checkNotNull(rootSchedulerClient);
  }

  public HousekeeperClient getHousekeeperClient() {
    return checkNotNull(housekeeperClient);
  }

  public DeployerClient getDeployerClient() {
    return checkNotNull(deployerClient);
  }

  public HostClient getHostClient(VmEntity vm)
      throws RpcException, InterruptedException, VmNotFoundException {
    return getHostClient(vm, true);
  }

  public HostClient getHostClient(VmEntity vm, boolean useCachedHostInfo)
      throws RpcException, InterruptedException, VmNotFoundException {
    checkNotNull(hostClient);
    if (useCachedHostInfo) {
      String hostIp = vm.getHost();
      if (StringUtils.isBlank(hostIp) && StringUtils.isNotBlank(vm.getAgent())) {
        try {
          hostIp = lookupHostIp(vm.getAgent());
        } catch (DocumentNotFoundException ex) {
          logger.error(String.format("Host %s does not exist.", vm.getAgent()), ex);
          throw new VmNotFoundException(vm.getId());
        }
      }
      if (StringUtils.isNotBlank(hostIp)) {
        hostClient.setHostIp(hostIp);
        return hostClient;
      }
    }
    invokeRootScheduler(vm);
    return hostClient;
  }

  public HostClient findHost(BaseDiskEntity disk)
      throws RpcException, InterruptedException, DiskNotFoundException {
    checkNotNull(hostClient);

    boolean foundDisk = false;
    if (disk.getAgent() != null) {
      try {
        String hostIp = lookupHostIp(disk.getAgent());
        hostClient.setHostIp(hostIp);
        foundDisk = hostClient.findDisk(disk.getId());
      } catch (DocumentNotFoundException ex) {
        logger.error(String.format("Host %s does not exist.", disk.getAgent()), ex);
        foundDisk = false;
      }
    }

    if (disk.getAgent() == null || !foundDisk) {
      invokeRootScheduler(disk);
    }

    return hostClient;
  }

  public String lookupAgentId(String hostIp) {
    checkNotNull(hostIp);

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(HostService.State.FIELD_NAME_HOST_ADDRESS, hostIp);

    List<String> result = dcpClient.queryDocumentsForLinks(HostService.State.class, termsBuilder.build());
    checkState(result.size() == 1, "Expect one and only one host with Address {}, found {}", hostIp, result.size());

    String agentId = ServiceUtils.getIDFromDocumentSelfLink(result.get(0));
    checkNotNull(agentId);

    return agentId;
  }

  private String lookupHostIp(String agentId) throws DocumentNotFoundException {
    checkNotNull(agentId);

    com.vmware.xenon.common.Operation result = dcpClient.get(HostServiceFactory.SELF_LINK + "/" + agentId);
    HostService.State hostState = result.getBody(HostService.State.class);

    return hostState.hostAddress;
  }

  private void invokeRootScheduler(BaseDiskEntity disk)
      throws RpcException, InterruptedException, DiskNotFoundException {
    logger.warn("disk {} does not have agentId, looking up from the scheduler", disk.getId());
    try {
      FindResponse response = rootSchedulerClient.findDisk(disk.getId());
      disk.setAgent(response.getAgent_id());
      ServerAddress serverAddress = response.getAddress();
      hostClient.setIpAndPort(serverAddress.getHost(), serverAddress.getPort());
    } catch (com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException ex) {
      throw new DiskNotFoundException(disk.getKind(), disk.getId());
    }
  }

  private void invokeRootScheduler(VmEntity vm)
      throws RpcException, InterruptedException, VmNotFoundException {
    logger.warn("vm {} does not have agentId or hostIp, looking up from the scheduler", vm.getId());
    try {
      FindResponse response = rootSchedulerClient.findVm(vm.getId());
      vm.setAgent(response.getAgent_id());
      if (response.getDatastore() != null) {
        vm.setDatastore(response.getDatastore().getId());
      }
      ServerAddress serverAddress = response.getAddress();
      hostClient.setIpAndPort(serverAddress.getHost(), serverAddress.getPort());
    } catch (com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException ex) {
      throw new VmNotFoundException(vm.getId());
    }
  }

}
