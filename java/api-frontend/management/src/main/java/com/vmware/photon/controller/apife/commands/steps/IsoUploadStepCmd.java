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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.lib.NfcClientUtils;
import com.vmware.photon.controller.apife.lib.VsphereIsoStore;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.DatastoreNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.transfer.nfc.HostServiceTicket;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.util.List;

/**
 * Step to upload an ISO.
 */
public class IsoUploadStepCmd extends StepCommand {

  public static final String INPUT_STREAM = "input-stream";

  private static final Logger logger = LoggerFactory.getLogger(IsoUploadStepCmd.class);

  private final VmBackend vmBackend;

  private final VsphereIsoStore isoStore;

  public IsoUploadStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                          VmBackend vmBackend, VsphereIsoStore isoStore) {
    super(taskCommand, stepBackend, step);
    this.vmBackend = vmBackend;
    this.isoStore = isoStore;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException {
    InputStream inputStream = (InputStream) step.getTransientResource(INPUT_STREAM);
    checkNotNull(inputStream, "InputStream is not defined in TransientResource");

    List<BaseEntity> vmList = step.getTransientResourceEntities(Vm.KIND);
    Preconditions.checkArgument(vmList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    VmEntity vmEntity = (VmEntity) vmList.get(0);

    List<IsoEntity> isoList = step.getTransientResourceEntities(IsoEntity.KIND);
    Preconditions.checkArgument(isoList.size() == 1,
        "There should be only 1 ISO referenced by step %s", step.getId());
    IsoEntity isoEntity = isoList.get(0);

    try {
      HostServiceTicket ticket = getNfcServiceTicketOp(vmEntity);

      isoStore.setTarget(ticket, vmEntity.getId(), vmEntity.getDatastoreName());
      long size = isoStore.uploadIsoFile(String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()),
          inputStream);
      vmBackend.updateIsoEntitySize(isoEntity, size);
    } catch (Exception e) {
      vmBackend.tombstoneIsoEntity(isoEntity);
      throw new ApiFeException(e);
    }
  }

  @Override
  protected void cleanup() {
  }

  private HostServiceTicket getNfcServiceTicketOp(VmEntity vmEntity)
      throws ApiFeException, InterruptedException, RpcException {
    HostClient hostClient = taskCommand.getHostClient(vmEntity);
    HostServiceTicket ticket;
    try {
      ticket = NfcClientUtils.convertToNfcHostServiceTicket(
          hostClient.getNfcServiceTicket(vmEntity.getDatastore()).getTicket(), hostClient.getHostIp());
    } catch (DatastoreNotFoundException ex) {
      vmEntity.setAgent(null);
      vmEntity.setHost(null);
      hostClient = taskCommand.getHostClient(vmEntity);
      ticket = NfcClientUtils.convertToNfcHostServiceTicket(
          hostClient.getNfcServiceTicket(vmEntity.getDatastore()).getTicket(), hostClient.getHostIp());
    }
    return ticket;
  }
}
