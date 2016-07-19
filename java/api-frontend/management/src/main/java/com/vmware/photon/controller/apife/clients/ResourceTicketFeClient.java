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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.backends.ResourceTicketBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ResourceTicketNotFoundException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Frontend client for resource ticket used by {@link ResourceTicketResource}.
 */
@Singleton
public class ResourceTicketFeClient {

  private final ResourceTicketBackend resourceTicketBackend;
  private final TaskBackend taskBackend;


  @Inject
  public ResourceTicketFeClient(ResourceTicketBackend resourceTicketBackend, TaskBackend taskBackend) {
    this.resourceTicketBackend = resourceTicketBackend;
    this.taskBackend = taskBackend;
  }

  public ResourceTicket get(String id) throws ResourceTicketNotFoundException {
    return resourceTicketBackend.getApiRepresentation(id);
  }

  public Task create(String tenantId, ResourceTicketCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = resourceTicketBackend.createResourceTicket(tenantId, spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public ResourceList<ResourceTicket> find(String tenantId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    return resourceTicketBackend.filter(tenantId, name, pageSize);
  }

  public ResourceList<ResourceTicket> getPage(String pageLink) throws ExternalException {
    return resourceTicketBackend.getPage(pageLink);
  }
}
