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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.clients.SchedulerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLocalitySpecException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.StaleGenerationException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.flavors.gen.Flavor;
import com.vmware.photon.controller.flavors.gen.QuotaLineItem;
import com.vmware.photon.controller.host.gen.CreateDiskError;
import com.vmware.photon.controller.host.gen.CreateDiskResultCode;
import com.vmware.photon.controller.host.gen.CreateDisksResponse;
import com.vmware.photon.controller.host.gen.CreateDisksResultCode;
import com.vmware.photon.controller.host.gen.ReserveResponse;
import com.vmware.photon.controller.host.gen.ReserveResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.ResourcePlacement;
import com.vmware.photon.controller.resource.gen.ResourcePlacementList;
import com.vmware.photon.controller.resource.gen.ResourcePlacementType;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTask;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link ResourceReserveStepCmd}.
 */
public class ResourceReserveStepCmdTest extends PowerMockTestCase {
  private static final CreateDisksResponse SUCCESSFUL_CREATE_DISKS_RESPONSE;

  private static final PlacementTask SUCCESSFUL_PLACEMENT_TASK;

  private static final ReserveResponse SUCCESSFUL_RESERVE_RESPONSE;

  private static final int SUCCESSFUL_GENERATION = 42;

  static {
    SUCCESSFUL_CREATE_DISKS_RESPONSE = new CreateDisksResponse(CreateDisksResultCode.OK);
    Disk disk = new Disk("disk-id", "core-100", false, true, 64);
    disk.setDatastore(new Datastore("datastore-id"));
    SUCCESSFUL_CREATE_DISKS_RESPONSE.addToDisks(disk);
    SUCCESSFUL_CREATE_DISKS_RESPONSE.putToDisk_errors("disk-id", new CreateDiskError(CreateDiskResultCode.OK));

    ServerAddress serverAddress = new ServerAddress();
    serverAddress.setHost("0.0.0.0");
    serverAddress.setPort(0);

    SUCCESSFUL_RESERVE_RESPONSE = new ReserveResponse(ReserveResultCode.OK);
    SUCCESSFUL_RESERVE_RESPONSE.setReservation("r-100");

    SUCCESSFUL_PLACEMENT_TASK = new PlacementTask();
    SUCCESSFUL_PLACEMENT_TASK.resultCode = PlaceResultCode.OK;
    SUCCESSFUL_PLACEMENT_TASK.serverAddress = serverAddress;
    SUCCESSFUL_PLACEMENT_TASK.generation = SUCCESSFUL_GENERATION;
    SUCCESSFUL_PLACEMENT_TASK.taskState = new TaskState();
    SUCCESSFUL_PLACEMENT_TASK.taskState.stage = TaskState.TaskStage.FINISHED;
  }

  @Mock
  private SchedulerXenonRestClient schedulerXenonRestClient;

  @Mock
  private HostClient hostClient;

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private DiskBackend diskBackend;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private FlavorBackend flavorBackend;

  @Mock
  private NetworkBackend networkBackend;

  @Captor
  private ArgumentCaptor<Resource> resourceCaptor;

  @Captor
  private ArgumentCaptor<PlacementTask> placementTaskCaptor;

  private ProjectEntity project;

  private VmEntity vm;

  private PersistentDiskEntity disk;

  @BeforeMethod
  public void setUp() throws ExternalException {
    project = new ProjectEntity();
    project.setId("project-id");
    project.setTenantId("tenant-id");

    List<QuotaLineItemEntity> quotaLineItemEntities = new ArrayList<>();
    quotaLineItemEntities.add(new QuotaLineItemEntity("vm.cost", 100.0, QuotaUnit.COUNT));

    List<QuotaLineItemEntity> quotaLineItemEntitiesForDisk = new ArrayList<>();
    quotaLineItemEntitiesForDisk.add(new QuotaLineItemEntity("storage.LOCAL_VMFS", 1.0, QuotaUnit.COUNT));

    FlavorEntity vmFlavorEntity = new FlavorEntity();
    vmFlavorEntity.setName("vm-100");
    vmFlavorEntity.setKind(Vm.KIND);
    vmFlavorEntity.setId(UUID.randomUUID().toString());

    FlavorEntity diskFlavorEntity = new FlavorEntity();
    diskFlavorEntity.setName("vm-100");
    diskFlavorEntity.setKind(PersistentDisk.KIND);
    diskFlavorEntity.setId(UUID.randomUUID().toString());

    vm = new VmEntity();
    vm.setId("foo");
    vm.setFlavorId(vmFlavorEntity.getId());
    vm.setCost(quotaLineItemEntities);
    vm.setProjectId(new String(project.getId()));

    disk = new PersistentDiskEntity();
    disk.setId("disk-1");
    disk.setFlavorId(diskFlavorEntity.getId());
    disk.setCost(quotaLineItemEntitiesForDisk);

    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getSchedulerXenonRestClient()).thenReturn(schedulerXenonRestClient);
    when(flavorBackend.getEntityById(vmFlavorEntity.getId())).thenReturn(vmFlavorEntity);
    when(flavorBackend.getEntityById(diskFlavorEntity.getId())).thenReturn(diskFlavorEntity);
  }

  @Test
  public void testSuccessfulVmExecution() throws Exception {
    List<QuotaLineItem> quotaLineItems = new ArrayList<>();
    quotaLineItems.add(new QuotaLineItem("vm.cost", "100.0", com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));

    Flavor expectedFlavor = new Flavor();
    expectedFlavor.setName("vm-100");
    expectedFlavor.setCost(quotaLineItems);

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.VM, "vm-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getVm().getId(), is("foo"));
    assertThat(resource.getVm().getFlavor(), is("vm-100"));
    assertThat(resource.getVm().getFlavor_info(), is(expectedFlavor));
    assertThat(resource.getVm().getProject_id(), is(project.getId()));
    assertThat(resource.getVm().getTenant_id(), is(project.getTenantId()));
    assertThat(resource.getVm().isSetResource_constraints(), is(false));

    assertThat(resource.getPlacement_list().getPlacements().size(), is(1));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getType(), is(ResourcePlacementType.VM));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getResource_id(), is("vm-id"));

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    verify(hostClient).reserve(resourceCaptor.capture(), eq(SUCCESSFUL_GENERATION));
    assertThat(placementTaskCaptor.getValue().resource, is(resource));
    assertThat(resourceCaptor.getValue(), is(resource));
  }


  @Test
  public void testSuccessfulVmExecutionWithDiskAffinities() throws Throwable {
    List<QuotaLineItem> quotaLineItems = new ArrayList<>();
    quotaLineItems.add(new QuotaLineItem("vm.cost", "100.0", com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));

    Flavor expectedFlavor = new Flavor();
    expectedFlavor.setName("vm-100");
    expectedFlavor.setCost(quotaLineItems);

    PersistentDiskEntity disk1 = new PersistentDiskEntity();
    PersistentDiskEntity disk2 = new PersistentDiskEntity();
    disk1.setDatastore("datastore-1");
    disk2.setDatastore("datastore-1");

    when(diskBackend.find(PersistentDisk.KIND, "disk-1")).thenReturn(disk1);
    when(diskBackend.find(PersistentDisk.KIND, "disk-2")).thenReturn(disk2);

    List<LocalityEntity> affinities = new ArrayList<>();
    LocalityEntity localityEntity1 = new LocalityEntity();
    localityEntity1.setResourceId("disk-1");
    localityEntity1.setKind("disk");
    LocalityEntity localityEntity2 = new LocalityEntity();
    localityEntity2.setResourceId("disk-2");
    localityEntity2.setKind("disk");

    affinities.add(localityEntity1);
    affinities.add(localityEntity2);
    vm.setAffinities(affinities);

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.VM, "vm-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getVm().getResource_constraints().size(), is(vm.getAffinities().size()));
    assertThat(resource.getVm().getResource_constraints().get(0).getType(), is(ResourceConstraintType.DATASTORE));
    assertThat(resource.getVm().getResource_constraints().get(0).getValues().equals(ImmutableList.of("datastore-1")),
        is(true));
    assertThat(resource.getVm().getResource_constraints().get(1).getType(), is(ResourceConstraintType.DATASTORE));
    assertThat(resource.getVm().getResource_constraints().get(1).getValues().equals(ImmutableList.of("datastore-1")),
        is(true));

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    verify(hostClient).reserve(resourceCaptor.capture(), eq(SUCCESSFUL_GENERATION));
    assertThat(placementTaskCaptor.getValue().resource, is(resource));
    assertThat(resourceCaptor.getValue(), is(resource));
  }

  @Test
  public void testSuccessfulVmExecutionWithAvailabilityZoneAffinities() throws Throwable {
    List<LocalityEntity> affinities = new ArrayList<>();
    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("availabilityZone-1");
    localityEntity.setKind("availabilityZone");

    affinities.add(localityEntity);
    vm.setAffinities(affinities);

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.VM, "vm-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(42))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getVm().getResource_constraints().size(), is(vm.getAffinities().size()));
    assertThat(resource.getVm().getResource_constraints().get(0).getType(),
        is(ResourceConstraintType.AVAILABILITY_ZONE));
    assertThat(resource.getVm().getResource_constraints().get(0).getValues()
            .equals(ImmutableList.of("availabilityZone-1")),
        is(true));

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    verify(hostClient).reserve(resourceCaptor.capture(), eq(SUCCESSFUL_GENERATION));
    assertThat(placementTaskCaptor.getValue().resource, is(resource));
    assertThat(resourceCaptor.getValue(), is(resource));
  }

  @Test
  public void testSuccessfulVmExecutionWithEphemeralDiskAttached() throws Exception {
    List<QuotaLineItem> quotaLineItems = new ArrayList<>();
    quotaLineItems.add(new QuotaLineItem("ephemeral-disk.cost", "10000.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));
    quotaLineItems.add(new QuotaLineItem("ephemeral-disk.capacity", "0.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.GB));
    quotaLineItems.add(new QuotaLineItem("storage.SHARED_VMFS", "1.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));

    Flavor expectedDiskFlavor = new Flavor();
    expectedDiskFlavor.setName("ephemeral-disk-10000");
    expectedDiskFlavor.setCost(quotaLineItems);

    attachEphemeralDisk(vm);
    ResourceReserveStepCmd command = getVmReservationCommand();

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.VM, "vm-id"));
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getVm().isSetResource_constraints(), is(false));
    assertThat(resource.getVm().getDisks().size(), is(vm.getAttachedDisks().size()));
    assertThat(resource.getVm().getDisks().get(0).getId(), is("disk1"));
    assertThat(resource.getVm().getDisks().get(0).getFlavor_info(), is(expectedDiskFlavor));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().size(), is(1));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(0).getType(), is(ResourceConstraintType
        .DATASTORE_TAG));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(0).getValues().equals(ImmutableList.of
        ("SHARED_VMFS")), is(true));

    assertThat(resource.getPlacement_list().getPlacements().size(), is(1 + vm.getAttachedDisks().size()));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getType(), is(ResourcePlacementType.VM));
    assertThat(resource.getPlacement_list().getPlacements().get(1).getType(), is(ResourcePlacementType.DISK));
  }

  @Test
  public void testSuccessfulVmExecutionWithEphemeralDiskAttachedAndDatastoreAffinity() throws Exception {
    List<LocalityEntity> affinities = new ArrayList<>();
    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("datastore-id");
    localityEntity.setKind("datastore");

    affinities.add(localityEntity);
    vm.setAffinities(affinities);

    List<QuotaLineItem> quotaLineItems = new ArrayList<>();
    quotaLineItems.add(new QuotaLineItem("ephemeral-disk.cost", "10000.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));
    quotaLineItems.add(new QuotaLineItem("ephemeral-disk.capacity", "0.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.GB));
    quotaLineItems.add(new QuotaLineItem("storage.SHARED_VMFS", "1.0",
        com.vmware.photon.controller.flavors.gen.QuotaUnit.COUNT));

    Flavor expectedDiskFlavor = new Flavor();
    expectedDiskFlavor.setName("ephemeral-disk-10000");
    expectedDiskFlavor.setCost(quotaLineItems);

    attachEphemeralDisk(vm);
    ResourceReserveStepCmd command = getVmReservationCommand();

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.VM, "vm-id"));
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getVm().isSetResource_constraints(), is(true));
    assertThat(resource.getVm().getDisks().size(), is(vm.getAttachedDisks().size()));
    assertThat(resource.getVm().getDisks().get(0).getId(), is("disk1"));
    assertThat(resource.getVm().getDisks().get(0).getFlavor_info(), is(expectedDiskFlavor));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().size(), is(2));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(0).getType(), is(ResourceConstraintType
        .DATASTORE_TAG));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(0).getValues().equals(ImmutableList.of
        ("SHARED_VMFS")), is(true));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(1).getType(), is(ResourceConstraintType
        .DATASTORE));
    assertThat(resource.getVm().getDisks().get(0).getResource_constraints().get(1).getValues().equals(ImmutableList.of
        ("datastore-id")), is(true));

    assertThat(resource.getPlacement_list().getPlacements().size(), is(1 + vm.getAttachedDisks().size()));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getType(), is(ResourcePlacementType.VM));
    assertThat(resource.getPlacement_list().getPlacements().get(1).getType(), is(ResourcePlacementType.DISK));
  }

  @Test(expectedExceptions = InternalException.class,
      expectedExceptionsMessageRegExp = "Project entity not found in the step.")
  public void testFailedVmExecutionNoProject() throws Throwable {
    ResourceReserveStepCmd command = getVmReservationCommand(false);
    command.execute();
  }

  @Test(expectedExceptions = InternalException.class,
      expectedExceptionsMessageRegExp = "Project entity in transient resource list did not match VMs project.")
  public void testFailedVmExecutionProjectEntityIdDoesNotMatchVmProjectId() throws Throwable {
    vm.setProjectId("some-other-id");
    ResourceReserveStepCmd command = getVmReservationCommand();
    command.execute();
  }

  @Test
  public void testSuccessfulDiskExecution() throws Exception {

    List<LocalityEntity> affinities = new ArrayList<>();
    LocalityEntity localityEntity1 = new LocalityEntity();
    localityEntity1.setResourceId("vm-1");
    localityEntity1.setKind("vm");
    LocalityEntity localityEntity2 = new LocalityEntity();
    localityEntity2.setResourceId("vm-2");
    localityEntity2.setKind("vm");

    affinities.add(localityEntity1);
    affinities.add(localityEntity2);
    disk.setAffinities(affinities);

    ResourceReserveStepCmd command = getDiskReservationCommand();

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);
    when(vmBackend.findDatastoreByVmId("vm-1")).thenReturn("datastore-2");
    when(vmBackend.findDatastoreByVmId("vm-2")).thenReturn("datastore-2");

    command.execute();

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    Resource resource = placementTaskCaptor.getValue().resource;
    assertThat(resource.getDisks().get(0).getId(), is("disk-1"));
    assertThat(resource.getDisks().get(0).getResource_constraints().get(0).getType(),
        is(ResourceConstraintType.DATASTORE));
    assertThat(
        resource.getDisks().get(0).getResource_constraints().get(0).getValues()
            .equals(ImmutableList.of("datastore-2")),
        is(true));
    assertThat(
        resource.getDisks().get(0).getResource_constraints().get(1).getValues()
            .equals(ImmutableList.of("datastore-2")),
        is(true));
    assertThat(resource.getDisks().get(0).getResource_constraints().get(2).getType(), is(ResourceConstraintType
        .DATASTORE_TAG));
    assertThat(resource.getDisks().get(0).getResource_constraints().get(2).getValues().equals(ImmutableList.of(
        "LOCAL_VMFS")), is(true));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getType(), is(ResourcePlacementType.DISK));
    assertThat(resource.getPlacement_list().getPlacements().get(0).getResource_id(), is("disk-id"));

    verify(schedulerXenonRestClient).post(any(), placementTaskCaptor.capture());
    verify(hostClient).reserve(resourceCaptor.capture(), eq(SUCCESSFUL_GENERATION));
    assertThat(placementTaskCaptor.getValue().resource, is(resource));
    assertThat(resourceCaptor.getValue(), is(resource));
  }

  @Test(expectedExceptions = StaleGenerationException.class)
  public void testFailedReservation() throws Throwable {
    ResourceReserveStepCmd command = getVmReservationCommand();
    Operation placementOperation = new Operation().setBody(generateResourcePlacementList());
    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION)))
        .thenThrow(new StaleGenerationException("Error"));

    command.execute();
  }

  @Test(expectedExceptions = XenonRuntimeException.class)
  public void testFailedPlacement() throws Throwable {
    ResourceReserveStepCmd command = getVmReservationCommand();
    when(schedulerXenonRestClient.post(any(), any())).thenThrow(new XenonRuntimeException("Error"));

    command.execute();
  }

  @Test
  public void testReservationFailedOnFirstTry() throws Throwable {
    ResourceReserveStepCmd command = getVmReservationCommand();

    Operation placementOperation = new Operation().setBody(generateResourcePlacementList());
    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION)))
        .thenThrow(new StaleGenerationException("Error"))
        .thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    command.execute();

    verify(schedulerXenonRestClient, times(2)).post(any(), any());
    verify(hostClient, times(2)).reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION));
  }

  @Test
  public void testOnFailureWithVmReservation() throws Throwable {
    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.markAsFailed(new RuntimeException("error"));

    InOrder inOrder = inOrder(stepBackend, vmBackend);
    inOrder.verify(stepBackend).markStepAsFailed(any(StepEntity.class), any(RuntimeException.class));
    inOrder.verify(vmBackend).updateState(vm, VmState.ERROR);
    verifyNoMoreInteractions(stepBackend, vmBackend);
  }

  @Test
  public void testOnFailureWithDiskReservation() throws Throwable {
    ResourceReserveStepCmd command = getDiskReservationCommand();
    command.setInfrastructureEntity(disk);
    command.markAsFailed(new RuntimeException("error"));

    InOrder inOrder = inOrder(stepBackend, diskBackend);
    inOrder.verify(stepBackend).markStepAsFailed(any(StepEntity.class), any(RuntimeException.class));
    verify(diskBackend).updateState(disk, DiskState.ERROR);
    verifyNoMoreInteractions(stepBackend, diskBackend);
  }

  @Test
  public void testSuccessfulVmReservationWithImageSpecifiedForBootDisk() throws Throwable {
    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    attachBootDisk(vm);
    vm.setImageId("");

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.execute();
  }

  @Test
  public void testCreateNetworkConstraints() throws Throwable {
    String networkId = "n1";
    Network network = new Network();
    network.setId(networkId);
    network.setPortGroups(ImmutableList.of("P1", "P2"));
    network.setState(NetworkState.READY);
    network.setName("public");
    when(networkBackend.toApiRepresentation(networkId)).thenReturn(network);

    vm.setNetworks(ImmutableList.of(networkId));

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.NETWORK, networkId));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), placementTaskCaptor.capture())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.execute();

    List<ResourceConstraint> resourceConstraints = placementTaskCaptor.getValue().resource.getVm()
        .getResource_constraints();
    assertThat(resourceConstraints.size(), is(1));
    ResourceConstraint resourceConstraint = resourceConstraints.get(0);
    assertThat(resourceConstraint.getType(), is(ResourceConstraintType.NETWORK));
    assertThat(resourceConstraint.getValues().size(), is(2));
    assertThat(resourceConstraint.getValues().get(0), is("P1"));
    assertThat(resourceConstraint.getValues().get(1), is("P2"));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testFailedVmReservationWithMissingImageForBootDisk() throws Throwable {
    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    attachBootDisk(vm);
    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.execute();
  }

  @Test(expectedExceptions = InvalidLocalitySpecException.class,
      expectedExceptionsMessageRegExp = "^vm locality is an unexpected constraint for creating a VM.$")
  public void testFailedOnVmLocalityAffinity() throws Throwable {
    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("vm-1");
    localityEntity.setKind("vm");

    vm.setAffinities(Arrays.asList(localityEntity));
    vm.setImageId("");

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    attachBootDisk(vm);
    vm.setImageId("");

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.execute();
  }

  @Test(expectedExceptions = InvalidLocalitySpecException.class,
      expectedExceptionsMessageRegExp = "^Blank resource constraint value for DATASTORE$")
  public void testFailedOnInvalidLocalityAffinity() throws Throwable {
    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("");
    localityEntity.setKind("datastore");

    vm.setAffinities(Arrays.asList(localityEntity));

    PlacementTask placementTask = generateResourcePlacementList();
    placementTask.resource.getPlacement_list().addToPlacements(
        generateResourcePlacement(ResourcePlacementType.DISK, "disk-id"));
    Operation placementOperation = new Operation().setBody(placementTask);

    when(schedulerXenonRestClient.post(any(), any())).thenReturn(placementOperation);
    when(hostClient.reserve(any(Resource.class), eq(SUCCESSFUL_GENERATION))).thenReturn(SUCCESSFUL_RESERVE_RESPONSE);

    attachBootDisk(vm);
    vm.setImageId("");

    ResourceReserveStepCmd command = getVmReservationCommand();
    command.setInfrastructureEntity(vm);
    command.execute();
  }

  private ResourceReserveStepCmd getVmReservationCommand() {
    return this.getVmReservationCommand(true);
  }

  private ResourceReserveStepCmd getVmReservationCommand(boolean addProjectEntity) {
    return getVmReservationCommand(addProjectEntity, new ArrayList<>());
  }

  private ResourceReserveStepCmd getVmReservationCommand(boolean addProjectEntity,
                                                         List<String> candidateImageDatastores) {
    StepEntity step = new StepEntity();
    step.setId("step-1");
    step.addResource(vm);

    TaskEntity task = new TaskEntity();
    task.setId("task-1");
    step.setTask(task);

    if (addProjectEntity) {
      step.addTransientResourceEntity(project);
    }

    return spy(new ResourceReserveStepCmd(
        taskCommand, stepBackend, step, diskBackend, vmBackend, networkBackend, flavorBackend));
  }

  private ResourceReserveStepCmd getDiskReservationCommand() {
    StepEntity step = new StepEntity();
    step.setId("step-1");
    step.addResource(disk);

    return spy(new ResourceReserveStepCmd(
        taskCommand, stepBackend, step, diskBackend, vmBackend, networkBackend, flavorBackend));
  }

  private void attachEphemeralDisk(VmEntity vm) throws ExternalException {
    List<QuotaLineItemEntity> quotaLineItemsEntities = new ArrayList<>();
    quotaLineItemsEntities.add(new QuotaLineItemEntity("ephemeral-disk.cost", 10000.0, QuotaUnit.COUNT));
    quotaLineItemsEntities.add(new QuotaLineItemEntity("ephemeral-disk.capacity", 0.0, QuotaUnit.GB));
    quotaLineItemsEntities.add(new QuotaLineItemEntity("storage.SHARED_VMFS", 1.0, QuotaUnit.COUNT));

    FlavorEntity ephemeralDiskFlavorEntity = new FlavorEntity();
    ephemeralDiskFlavorEntity.setName("ephemeral-disk-10000");
    ephemeralDiskFlavorEntity.setKind(EphemeralDisk.KIND);
    ephemeralDiskFlavorEntity.setId(UUID.randomUUID().toString());

    when(flavorBackend.getEntityById(ephemeralDiskFlavorEntity.getId())).thenReturn(ephemeralDiskFlavorEntity);

    BaseDiskEntity disk = new EphemeralDiskEntity();
    disk.setId("disk1");
    disk.setFlavorId(ephemeralDiskFlavorEntity.getId());
    disk.setCost(quotaLineItemsEntities);

    AttachedDiskEntity disk1 = new AttachedDiskEntity();
    disk1.setUnderlyingDiskIdAndKind(disk);
    vm.addAttachedDisk(disk1);
    when(diskBackend.find(disk1.getKind(), disk1.getUnderlyingDiskId())).thenReturn(disk);
  }

  private void attachBootDisk(VmEntity vm) throws ExternalException {
    FlavorEntity bootDiskFlavorEntity = new FlavorEntity();
    bootDiskFlavorEntity.setName("persistent-boot-disk");
    bootDiskFlavorEntity.setKind(PersistentDisk.KIND);
    bootDiskFlavorEntity.setId(UUID.randomUUID().toString());

    BaseDiskEntity disk = new PersistentDiskEntity();
    disk.setId("disk1");
    disk.setFlavorId(bootDiskFlavorEntity.getId());

    when(flavorBackend.getEntityById(bootDiskFlavorEntity.getId())).thenReturn(bootDiskFlavorEntity);

    AttachedDiskEntity disk1 = new AttachedDiskEntity();
    disk1.setBootDisk(true);
    disk1.setUnderlyingDiskIdAndKind(disk);
    vm.addAttachedDisk(disk1);
    when(diskBackend.find(disk1.getKind(), disk1.getUnderlyingDiskId())).thenReturn(disk);
  }

  private PlacementTask generateResourcePlacementList() {
    PlacementTask placementTask = new PlacementTask();
    placementTask.resultCode = SUCCESSFUL_PLACEMENT_TASK.resultCode;
    placementTask.serverAddress = SUCCESSFUL_PLACEMENT_TASK.serverAddress;
    placementTask.generation = SUCCESSFUL_PLACEMENT_TASK.generation;
    placementTask.taskState = SUCCESSFUL_PLACEMENT_TASK.taskState;
    if (placementTask.resource == null) {
      placementTask.resource = new Resource();
      ResourcePlacementList resourcePlacementList = new ResourcePlacementList();
      resourcePlacementList.setPlacements(new ArrayList<ResourcePlacement>());
      placementTask.resource.setPlacement_list(resourcePlacementList);
    }
    return placementTask;
  }

  private ResourcePlacement generateResourcePlacement(ResourcePlacementType type, String id) {
    ResourcePlacement resourcePlacement = new ResourcePlacement();
    resourcePlacement.setType(type);
    resourcePlacement.setResource_id(id);

    return resourcePlacement;
  }
}
