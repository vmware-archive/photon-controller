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

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostNotFoundException;

import java.util.List;

/**
 * The Host Backend Interface.
 */

public interface HostBackend {

  TaskEntity prepareHostCreate(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException;

  TaskEntity prepareHostDelete(String id) throws ExternalException;

  List<Host> listAll();

  HostEntity findById(String id) throws HostNotFoundException;

  List<Host> filterByUsage(UsageTag usageTag);

  Host toApiRepresentation(String id) throws HostNotFoundException;

  void updateState(HostEntity entity, HostState state) throws HostNotFoundException;

  void tombstone(HostEntity hostEntity);

  TaskEntity resume(String hostId) throws ExternalException;

  TaskEntity suspend(String hostId) throws ExternalException;

  TaskEntity enterMaintenance(String hostId) throws ExternalException;

  TaskEntity exitMaintenance(String hostId) throws ExternalException;
}
