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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.backends.FlavorBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.PathParam;

/**
 * Frontend client for flavor used by {@link FlavorsResource}.
 */
@Singleton
public class FlavorFeClient {
  private static final Logger logger = LoggerFactory.getLogger(FlavorFeClient.class);

  private final FlavorBackend flavorBackend;
  private final TaskBackend taskBackend;

  @Inject
  public FlavorFeClient(FlavorBackend flavorBackend, TaskBackend taskBackend) {
    this.flavorBackend = flavorBackend;
    this.taskBackend = taskBackend;
  }

  public Flavor get(@PathParam("id") String id) throws ExternalException {
    return flavorBackend.getApiRepresentation(id);
  }

  public Task create(FlavorCreateSpec flavor) throws ExternalException {
    TaskEntity taskEntity = flavorBackend.createFlavor(flavor);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = flavorBackend.prepareFlavorDelete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public ResourceList<Flavor> list(Optional<String> name, Optional<String> kind, Optional<Integer> pageSize)
          throws ExternalException {
    return flavorBackend.filter(name, kind, pageSize);
  }

  public ResourceList<Flavor> getFlavorsPage(String pageLink) throws PageExpiredException{
    return flavorBackend.getFlavorsPage(pageLink);
  }
}
