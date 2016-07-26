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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Frontend client for disk used by {@link DiskResource}.
 */
@Singleton
public class DiskFeClient {
  private static final Logger logger = LoggerFactory.getLogger(DiskFeClient.class);

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;
  private final DiskBackend diskBackend;
  private final TaskBackend taskBackend;


  @Inject
  public DiskFeClient(TaskCommandFactory commandFactory, DiskBackend diskBackend,
                      @BackendTaskExecutor ExecutorService executor, TaskBackend taskBackend) {
    this.commandFactory = commandFactory;
    this.executor = executor;
    this.diskBackend = diskBackend;
    this.taskBackend = taskBackend;
  }

  public Task create(String projectId, DiskCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task delete(String diskId) throws ExternalException {
    TaskEntity taskEntity = diskBackend.prepareDiskDelete(diskId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public PersistentDisk get(String id) throws ExternalException {
    return diskBackend.toApiRepresentation(id);
  }

  public ResourceList<PersistentDisk> find(String projectId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    return diskBackend.filter(projectId, name, pageSize);
  }

  public ResourceList<PersistentDisk> getDisksPage(String pageLink) throws ExternalException {
    return diskBackend.getDisksPage(pageLink);
  }
}
