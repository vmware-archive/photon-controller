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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneBackend;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Frontend client for task used by {@link TaskResource}.
 */
@Singleton
public class TaskFeClient {

  private final TaskBackend taskBackend;

  private final TenantBackend tenantBackend;

  private final ProjectBackend projectBackend;

  private final ResourceTicketBackend resourceTicketBackend;

  private final VmBackend vmBackend;

  private final DiskBackend diskBackend;

  private final ImageBackend imageBackend;

  private final FlavorBackend flavorBackend;

  private final HostBackend hostBackend;

  private final AvailabilityZoneBackend availabilityZoneBackend;

  @Inject
  public TaskFeClient(TaskBackend taskBackend, TenantBackend tenantBackend, ProjectBackend projectBackend,
                      ResourceTicketBackend resourceTicketBackend, VmBackend vmBackend, DiskBackend diskBackend,
                      ImageBackend imageBackend, FlavorBackend flavorBackend, HostBackend hostBackend,
                      AvailabilityZoneBackend availabilityZoneBackend) {
    this.taskBackend = taskBackend;
    this.tenantBackend = tenantBackend;
    this.projectBackend = projectBackend;
    this.resourceTicketBackend = resourceTicketBackend;
    this.vmBackend = vmBackend;
    this.diskBackend = diskBackend;
    this.imageBackend = imageBackend;
    this.flavorBackend = flavorBackend;
    this.hostBackend = hostBackend;
    this.availabilityZoneBackend = availabilityZoneBackend;
  }

  public Task get(String id) throws ExternalException {
    return taskBackend.getApiRepresentation(id);
  }

  public ResourceList<Task> find(Optional<String> entityId, Optional<String> entityKind, Optional<String> state,
                                 Optional<Integer> pageSize)
      throws ExternalException {
    return new ResourceList<>(taskBackend.filter(entityId, entityKind, state, pageSize));
  }

  public ResourceList<Task> getTenantTasks(String tenantId, Optional<String> state)
      throws ExternalException {
    tenantBackend.findById(tenantId);
    return new ResourceList<>(taskBackend.filter(Optional.of(tenantId), Optional.of(TenantEntity.KIND), state,
        Optional.<Integer>absent()));
  }

  public ResourceList<Task> getProjectTasks(String projectId, Optional<String> state,
                                            Optional<String> kind) throws ExternalException {
    projectBackend.findById(projectId);
    return new ResourceList<>(taskBackend.filterInProject(projectId, state, kind));
  }

  public ResourceList<Task> getResourceTicketTasks(String resourceTicketId, Optional<String> state)
      throws ExternalException {
    resourceTicketBackend.findById(resourceTicketId);
    return new ResourceList<>(
        taskBackend.filter(Optional.of(resourceTicketId), Optional.of(ResourceTicketEntity.KIND), state,
            Optional.<Integer>absent()));
  }

  public ResourceList<Task> getVmTasks(String vmId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(vmBackend.getTasks(vmId, state));
  }

  public ResourceList<Task> getDiskTasks(String diskId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(diskBackend.getTasks(diskId, state));
  }

  public ResourceList<Task> getImageTasks(String imageId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(imageBackend.getTasks(imageId, state));
  }

  public ResourceList<Task> getFlavorTasks(String flavorId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(flavorBackend.getTasks(flavorId, state));
  }

  public ResourceList<Task> getHostTasks(String hostId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(hostBackend.getTasks(hostId, state));
  }

  public ResourceList<Task> getAvailabilityZoneTasks(String availabilityZoneId, Optional<String> state)
      throws ExternalException {
    return new ResourceList<>(availabilityZoneBackend.getTasks(availabilityZoneId, state));
  }
}
