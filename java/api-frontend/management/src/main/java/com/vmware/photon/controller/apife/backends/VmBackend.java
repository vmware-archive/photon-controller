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

import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;

import com.google.common.base.Optional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * VmBackend is performing VM operations such as create, delete, add tag etc.
 */
public interface VmBackend {
  List<Vm> filter(String projectId, Optional<String> name) throws ExternalException;

  List<Vm> filterByProject(String projectId) throws ExternalException;

  List<Vm> filterByTag(String projectId, Tag tag) throws ExternalException;

  List<Vm> filterByFlavor(String flavorId) throws ExternalException;

  List<Vm> filterByImage(String imageId) throws ExternalException;

  List<Vm> filterByNetwork(String networkId) throws ExternalException;

  String findDatastoreByVmId(String id) throws VmNotFoundException;

  Vm toApiRepresentation(String id) throws ExternalException;

  List<Task> getTasks(String id, Optional<String> state, Optional<Integer> pageSize) throws ExternalException;

  void tombstone(VmEntity vm) throws ExternalException;

  void updateState(VmEntity vm, VmState state) throws VmNotFoundException, DiskNotFoundException;

  void updateState(VmEntity vm, VmState state, String agent, String agentIp,
                   String datastoreId, String datastoreName) throws ExternalException;

  TaskEntity addTag(String vmId, Tag tag) throws ExternalException;

  TaskEntity prepareVmCreate(String projectId, VmCreateSpec spec) throws ExternalException;

  TaskEntity prepareVmDelete(String vmId) throws ExternalException;

  TaskEntity prepareVmOperation(String vmId, Operation operation) throws ExternalException;

  TaskEntity prepareVmDiskOperation(String vmId, List<String> diskIds, Operation operation)
      throws ExternalException;

  TaskEntity prepareVmAttachIso(String vmId, InputStream inputStream,
                                String isoFileName) throws ExternalException;

  TaskEntity prepareVmDetachIso(String vmId) throws ExternalException;

  TaskEntity prepareVmGetNetworks(String vmId) throws ExternalException;

  TaskEntity prepareVmGetMksTicket(String vmId) throws ExternalException;

  List<IsoEntity> isosAttached(VmEntity vmEntity) throws VmNotFoundException;

  void detachIso(VmEntity vmEntity) throws ExternalException;

  List<Vm> getAllVmsOnHost(String hostId) throws ExternalException;

  int countVmsOnHost(HostEntity hostEntity) throws ExternalException;

  TaskEntity prepareSetMetadata(String id, Map<String, String> metadata) throws ExternalException;

  TaskEntity prepareVmCreateImage(String vmId, ImageCreateSpec imageCreateSpec)
      throws ExternalException;

  VmEntity findById(String id) throws VmNotFoundException;

  void updateIsoEntitySize(IsoEntity isoEntity, long size);

  void tombstoneIsoEntity(IsoEntity isoEntity);

  void addIso(IsoEntity isoEntity, VmEntity vmEntity) throws VmNotFoundException;

}
