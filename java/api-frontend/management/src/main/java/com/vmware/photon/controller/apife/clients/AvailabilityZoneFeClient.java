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

import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.AvailabilityZoneCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.backends.AvailabilityZoneBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.PathParam;

/**
 * Frontend client for AvailabilityZone used by {@link AvailabilityZonesResource}.
 */
@Singleton
public class AvailabilityZoneFeClient {
  private static final Logger logger = LoggerFactory.getLogger(AvailabilityZoneFeClient.class);

  private final AvailabilityZoneBackend availabilityZoneBackend;
  private final TaskBackend taskBackend;

  @Inject
  public AvailabilityZoneFeClient(AvailabilityZoneBackend availabilityZoneBackend, TaskBackend taskBackend) {
    this.availabilityZoneBackend = availabilityZoneBackend;
    this.taskBackend = taskBackend;
  }

  public AvailabilityZone get(@PathParam("id") String id) throws ExternalException {
    return availabilityZoneBackend.getApiRepresentation(id);
  }

  public Task create(AvailabilityZoneCreateSpec availabilityZone) throws ExternalException {
    TaskEntity taskEntity = availabilityZoneBackend.createAvailabilityZone(availabilityZone);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = availabilityZoneBackend.prepareAvailabilityZoneDelete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public ResourceList<AvailabilityZone> list(Optional<Integer> pageSize) throws ExternalException {
    return availabilityZoneBackend.getListApiRepresentation(pageSize);
  }

  public ResourceList<AvailabilityZone> listPage(String pageLink) throws ExternalException {
    return availabilityZoneBackend.getPage(pageLink);
  }
}
