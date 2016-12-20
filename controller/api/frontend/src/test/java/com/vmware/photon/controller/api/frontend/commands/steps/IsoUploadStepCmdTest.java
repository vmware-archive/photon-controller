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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.IsoEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.lib.VsphereIsoStore;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.DatastoreNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResultCode;
import com.vmware.photon.controller.resource.gen.HostServiceTicket;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Test {@link IsoUploadStepCmd}.
 */
public class IsoUploadStepCmdTest extends PowerMockTestCase {
  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HostClient hostClient;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private VsphereIsoStore vsphereIsoStore;


  private StepEntity step;
  private IsoEntity isoEntity;
  private VmEntity vmEntity;
  private IsoUploadStepCmd command;
  private ServiceTicketResponse serviceTicketResponse;
  private HostServiceTicket hostServiceTicket;
  private String isoContent = "test content";
  private InputStream isoStream = new ByteArrayInputStream(isoContent.getBytes());
  private long isoSize = isoContent.length();

  @BeforeClass
  public void setUp() throws Exception {
    vmEntity = new VmEntity();
    vmEntity.setId("vm-1");
    vmEntity.setDatastore("datastore-id");

    isoEntity = new IsoEntity();
    isoEntity.setId("iso-id");
    isoEntity.setName("iso-name");

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(vmEntity);
    step.addResource(isoEntity);

    hostServiceTicket = new HostServiceTicket();
    serviceTicketResponse = new ServiceTicketResponse(ServiceTicketResultCode.OK);
    serviceTicketResponse.setTicket(hostServiceTicket);
  }

  @BeforeMethod
  public void beforeMethod() throws Exception {
    command = new IsoUploadStepCmd(taskCommand, stepBackend, step, vmBackend, vsphereIsoStore);
    doNothing().when(vmBackend).updateIsoEntitySize(isoEntity, isoSize);
    when(taskCommand.getHostClient(vmEntity)).thenReturn(hostClient);
    doNothing().when(vsphereIsoStore).setTarget(any(com.vmware.transfer.nfc.HostServiceTicket.class),
        anyString(), anyString());
  }

  @Test
  public void testSuccessfulUpload() throws Exception {
    when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, isoStream);
    when(vsphereIsoStore.uploadIsoFile(anyString(), any(InputStream.class))).thenReturn(isoSize);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vsphereIsoStore, vmBackend);
    inOrder.verify(hostClient).getNfcServiceTicket(vmEntity.getDatastore());
    inOrder.verify(hostClient).getHostIp();
    inOrder.verify(vsphereIsoStore).setTarget(any(com.vmware.transfer.nfc.HostServiceTicket.class),
        anyString(), anyString());
    inOrder.verify(vsphereIsoStore).uploadIsoFile(
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()), isoStream);
    inOrder.verify(vmBackend).updateIsoEntitySize(isoEntity, isoSize);
    verifyNoMoreInteractions(hostClient, vsphereIsoStore, vmBackend);
  }

  @Test
  public void testFailedGetServiceTicket() throws Throwable {
    when(hostClient.getNfcServiceTicket(anyString())).thenThrow(new SystemErrorException("e"));
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, isoStream);

    try {
      command.execute();
      fail("should have failed due to SystemErrorException exception");
    } catch (Exception e) {
    }
  }

  @Test
  public void testFailedUploadIso() throws Exception {
    when(hostClient.getNfcServiceTicket(anyString())).thenReturn(serviceTicketResponse);
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, isoStream);
    doThrow(new RuntimeException("ERROR ISO")).when(vsphereIsoStore).uploadIsoFile(anyString(),
        any(InputStream.class));

    try {
      command.execute();
      fail("Exception expected.");
    } catch (ApiFeException e) {
      assertEquals(e.getCause().getMessage(), "ERROR ISO");
    }

    InOrder inOrder = inOrder(hostClient, vsphereIsoStore, vmBackend);
    inOrder.verify(hostClient).getNfcServiceTicket(vmEntity.getDatastore());
    inOrder.verify(hostClient).getHostIp();
    inOrder.verify(vsphereIsoStore).setTarget(any(com.vmware.transfer.nfc.HostServiceTicket.class),
        anyString(), anyString());
    inOrder.verify(vsphereIsoStore).uploadIsoFile(
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()), isoStream);
    inOrder.verify(vmBackend).tombstoneIsoEntity(isoEntity);
    verifyNoMoreInteractions(hostClient, vsphereIsoStore, vmBackend);
  }

  @Test
  public void testStaleAgent() throws Exception {
    when(hostClient.getNfcServiceTicket(anyString())).thenThrow(new DatastoreNotFoundException("datastore not found"))
        .thenReturn(serviceTicketResponse);
    step.createOrUpdateTransientResource(ImageUploadStepCmd.INPUT_STREAM, isoStream);
    when(vsphereIsoStore.uploadIsoFile(anyString(), any(InputStream.class))).thenReturn(isoSize);

    command.execute();

    InOrder inOrder = inOrder(hostClient, vsphereIsoStore, vmBackend);
    inOrder.verify(hostClient, times(2)).getNfcServiceTicket(vmEntity.getDatastore());
    inOrder.verify(hostClient).getHostIp();
    inOrder.verify(vsphereIsoStore).setTarget(any(com.vmware.transfer.nfc.HostServiceTicket.class),
        anyString(), anyString());
    inOrder.verify(vsphereIsoStore).uploadIsoFile(
        String.format("%s/%s", vmEntity.buildVmFolderPath(), isoEntity.getName()), isoStream);
    inOrder.verify(vmBackend).updateIsoEntitySize(isoEntity, isoSize);
    verifyNoMoreInteractions(hostClient, vsphereIsoStore, vmBackend);
  }
}
