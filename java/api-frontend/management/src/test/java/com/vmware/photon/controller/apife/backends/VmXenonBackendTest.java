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

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.DiskType;
import com.vmware.photon.controller.api.HostCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Iso;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmOperation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.builders.AttachedDiskCreateSpecBuilder;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.steps.ResourceReserveStepCmd;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAttachDisksException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Operation;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tests {@link VmXenonBackend}.
 */
public class VmXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;
  private static String projectId;
  private static VmCreateSpec vmCreateSpec;
  private static TaskEntity createdVmTaskEntity;
  private static String imageId;
  private static ImageService.State createdImageState;

  @Test
  private void dummy() {
  }

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }

  private static void commonDataSetup(
      TenantXenonBackend tenantXenonBackend,
      ResourceTicketXenonBackend resourceTicketXenonBackend,
      ProjectXenonBackend projectXenonBackend,
      FlavorXenonBackend flavorXenonBackend,
      FlavorLoader flavorLoader) throws Throwable {
    String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

    QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
    XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
        tenantId, "rt1", ImmutableList.of(ticketLimit));

    QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
    projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
        "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

    XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());
  }

  private static void commonVmAndImageSetup(VmXenonBackend vmXenonBackend, NetworkXenonBackend networkZenonBackend)
      throws Throwable {
    AttachedDiskCreateSpec disk1 =
        new AttachedDiskCreateSpecBuilder().name("disk1").flavor("core-100").bootDisk(true).build();
    AttachedDiskCreateSpec disk2 =
        new AttachedDiskCreateSpecBuilder().name("disk2").flavor("core-200").capacityGb(10).build();

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("disk-id1", "disk"));
    affinities.add(new LocalitySpec("disk-id2", "disk"));

    ImageService.State imageServiceState = new ImageService.State();
    imageServiceState.name = "image-1";
    imageServiceState.state = ImageState.READY;
    imageServiceState.size = 1024L * 1024L;
    imageServiceState.replicationType = ImageReplicationType.EAGER;
    imageServiceState.imageSettings = new ArrayList<>();
    ImageService.State.ImageSetting imageSetting = new ImageService.State.ImageSetting();
    imageSetting.name = "n1";
    imageSetting.defaultValue = "v1";
    imageServiceState.imageSettings.add(imageSetting);
    imageSetting = new ImageService.State.ImageSetting();
    imageSetting.name = "n2";
    imageSetting.defaultValue = "v2";
    imageServiceState.imageSettings.add(imageSetting);

    Operation result = xenonClient.post(ImageServiceFactory.SELF_LINK, imageServiceState);
    createdImageState = result.getBody(ImageService.State.class);
    imageId = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);

    // create networks
    createHostDocument(host);

    List<String> networks = new ArrayList<>();
    List<String> portGroups = new ArrayList<>();
    portGroups.add("p1");
    NetworkCreateSpec networkCreateSpec = new NetworkCreateSpec();
    networkCreateSpec.setName("n1");
    networkCreateSpec.setPortGroups(portGroups);
    TaskEntity networkTask = networkZenonBackend.createNetwork(networkCreateSpec);
    networks.add(networkTask.getEntityId());

    portGroups = new ArrayList<>();
    portGroups.add("p2");
    networkCreateSpec.setName("n2");
    networkCreateSpec.setPortGroups(portGroups);
    networkTask = networkZenonBackend.createNetwork(networkCreateSpec);
    networks.add(networkTask.getEntityId());

    vmCreateSpec = new VmCreateSpec();
    vmCreateSpec.setName("test-vm");
    vmCreateSpec.setFlavor("core-100");
    vmCreateSpec.setSourceImageId(imageId);
    vmCreateSpec.setAttachedDisks(ImmutableList.of(disk1, disk2));
    vmCreateSpec.setAffinities(affinities);
    vmCreateSpec.setTags(ImmutableSet.of("value1", "value2"));
    vmCreateSpec.setNetworks(networks);
    createdVmTaskEntity = vmXenonBackend.prepareVmCreate(projectId, vmCreateSpec);
  }

  private static void createHostDocument(BasicServiceHost host) throws Throwable {
    host.startFactoryServiceSynchronously(new HostServiceFactory(), HostServiceFactory.SELF_LINK);

    HostService.State hostDoc = new HostService.State();
    hostDoc.state = HostState.READY;
    hostDoc.hostAddress = "10.0.0.0";
    hostDoc.userName = "user";
    hostDoc.password = "pwd";

    hostDoc.usageTags = new HashSet<>();
    hostDoc.usageTags.add(UsageTag.CLOUD.toString());

    hostDoc.reportedNetworks = new HashSet<>();
    hostDoc.reportedNetworks.add("p1");
    hostDoc.reportedNetworks.add("p2");

    Operation post = Operation.createPost(host, HostServiceFactory.SELF_LINK).setBody(hostDoc);
    host.sendRequestAndWait(post);
  }

  /**
   * Tests for getting Vms.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class GetVmTest {

    private VmService.State vm;
    private VmService.State createdVm;
    private String vmId;
    private FlavorEntity flavorEntity;

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @Inject
    private HostBackend hostBackend;

    @Inject
    private DeploymentBackend deploymentBackend;

    private String hostId;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      vm = new VmService.State();
      vm.name = UUID.randomUUID().toString();
      flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-100", Vm.KIND);
      vm.flavorId = flavorEntity.getId();
      vm.imageId = UUID.randomUUID().toString();
      vm.projectId = projectId;
      vm.vmState = VmState.CREATING;

      vm.affinities = new ArrayList<>();
      vm.affinities.add(new LocalitySpec("id1", "kind1"));


      Iso iso = new Iso();
      iso.setName(UUID.randomUUID().toString());
      iso.setSize(-1L);
      vm.isos = new ArrayList<>();
      vm.isos.add(iso);


      vm.metadata = new HashMap<>();
      vm.metadata.put("key1", UUID.randomUUID().toString());

      vm.networks = new ArrayList<>();
      vm.networks.add(UUID.randomUUID().toString());
      vm.agent = UUID.randomUUID().toString();
      vm.host = UUID.randomUUID().toString();
      vm.datastore = UUID.randomUUID().toString();
      vm.datastoreName = UUID.randomUUID().toString();

      vm.tags = new HashSet<>();
      vm.tags.add("namespace1:predicate1=value1");
      vm.tags.add("namespace2:predicate2=value2");

      Operation result = xenonClient.post(VmServiceFactory.SELF_LINK, vm);
      createdVm = result.getBody(VmService.State.class);
      vmId = ServiceUtils.getIDFromDocumentSelfLink(createdVm.documentSelfLink);

      DeploymentCreateSpec deploymentCreateSpec = new DeploymentCreateSpec();
      deploymentCreateSpec.setImageDatastores(Collections.singleton(UUID.randomUUID().toString()));
      TaskEntity deploymentTask = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);

      HostCreateSpec hostCreateSpec = new HostCreateSpec();
      hostCreateSpec.setAddress(vm.host);
      hostCreateSpec.setUsageTags(ImmutableList.of(UsageTag.CLOUD));
      hostCreateSpec.setUsername(UUID.randomUUID().toString());
      hostCreateSpec.setPassword(UUID.randomUUID().toString());
      TaskEntity hostTask = hostBackend.prepareHostCreate(hostCreateSpec, deploymentTask.getEntityId());
      hostId = hostTask.getEntityId();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testFindById() throws Throwable {
      VmEntity foundVmEntity = vmXenonBackend.findById(vmId);
      assertThat(foundVmEntity.getName(), is(vm.name));
      assertThat(foundVmEntity.getFlavorId(), is(vm.flavorId));
      assertThat(foundVmEntity.getImageId(), is(vm.imageId));
      assertThat(foundVmEntity.getProjectId(), is(vm.projectId));
      assertThat(foundVmEntity.getState(), is(vm.vmState));

      assertThat(foundVmEntity.getAffinities().get(0).getResourceId(),
          is(vm.affinities.get(0).getId()));
      assertThat(foundVmEntity.getAffinities().get(0).getKind(),
          is(vm.affinities.get(0).getKind()));

      assertThat(foundVmEntity.getIsos().get(0).getName(),
          is(vm.isos.get(0).getName()));
      assertThat(foundVmEntity.getIsos().get(0).getSize(),
          is(vm.isos.get(0).getSize()));

      assertThat(foundVmEntity.getMetadata().get("key1"),
          is(vm.metadata.get("key1")));

      assertThat(foundVmEntity.getNetworks().get(0),
          is(vm.networks.get(0)));

      assertThat(vm.tags.contains(foundVmEntity.getTags().iterator().next().getValue()),
          is(true));

      assertThat(foundVmEntity.getAgent(), is(vm.agent));
      assertThat(foundVmEntity.getHost(), is(vm.host));
      assertThat(foundVmEntity.getDatastore(), is(vm.datastore));
      assertThat(foundVmEntity.getDatastoreName(), is(vm.datastoreName));
    }

    @Test
    public void testFindByIdWithNonExistingId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        vmXenonBackend.findById(id);
        fail("vmXenonBackend.findById for a non existing id should have failed");
      } catch (VmNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }

    @Test
    public void testFilter() throws Throwable {
      ResourceList<Vm> foundVms =
          vmXenonBackend.filter(vm.projectId, Optional.<String> absent(), Optional.<Integer> absent());
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.getItems().size(), is(1));
      assertThat(foundVms.getItems().get(0).getName(), is(vm.name));

      foundVms = vmXenonBackend.filter(vm.projectId, Optional.of(vm.name), Optional.<Integer> absent());
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.getItems().size(), is(1));
      assertThat(foundVms.getItems().get(0).getName(), is(vm.name));
    }

    @Test
    public void testFilterByTag() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.filterByTag(vm.projectId, new Tag(vm.tags.iterator().next()),
          Optional.<Integer>absent()).getItems();
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(1));
      assertThat(foundVms.get(0).getName(), is(vm.name));
    }

    @Test
    public void testFilterByTagNoMatch() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.filterByTag(vm.projectId, new Tag("tag1"),
          Optional.<Integer>absent()).getItems();
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(0));
    }

    @Test
    public void testFilterByTagPagination() throws Throwable {
      ResourceList<Vm> foundVms = vmXenonBackend.filterByTag(vm.projectId, new Tag(vm.tags.iterator().next()),
          Optional.of(1));
      assertThat(foundVms.getItems(), is(notNullValue()));
      assertThat(foundVms.getItems().size(), is(1));
      assertThat(foundVms.getItems().get(0).getName(), is(vm.name));
    }

    @Test
    public void testFilterByFlavor() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.filterByFlavor(vm.flavorId);
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(1));
      assertThat(foundVms.get(0).getName(), is(vm.name));
    }

    @Test
    public void testFilterByImage() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.filterByImage(vm.imageId);
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(1));
      assertThat(foundVms.get(0).getName(), is(vm.name));
    }

    @Test
    public void testFilterByNetwork() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.filterByNetwork(vm.networks.get(0));
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(1));
      assertThat(foundVms.get(0).getName(), is(vm.name));
    }

    @Test
    public void testFindByProjectId() throws Throwable {
      ResourceList<Vm> foundVms = vmXenonBackend.filterByProject(vm.projectId,
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.getItems().size(), is(1));
      assertThat(foundVms.getItems().get(0).getName(), is(vm.name));
    }

    @Test
    public void testWithNonExistingProjectId() throws Throwable {
      String id = UUID.randomUUID().toString();
      try {
        vmXenonBackend.filterByProject(id, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
        fail("vmXenonBackend.filterByProject for a non existing projectId should have failed");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), containsString(id));
      }
    }

    @Test
    public void testFindByDatastoreByVmId() throws Throwable {
      String datastore = vmXenonBackend.findDatastoreByVmId(vmId);
      assertThat(datastore, is(notNullValue()));
      assertThat(datastore, is(vm.datastore));
    }

    @Test
    public void testGetAllVmsOnHost() throws Throwable {
      List<Vm> foundVms = vmXenonBackend.getAllVmsOnHost(hostId, Optional.<Integer> absent()).getItems();
      assertThat(foundVms, is(notNullValue()));
      assertThat(foundVms.size(), is(1));
      assertThat(foundVms.get(0).getName(), is(vm.name));
    }

    @Test
    public void testCountVmsOnHost() throws Throwable {
      HostEntity hostEntity = new HostEntity();
      hostEntity.setAddress(vm.host);
      hostEntity.setId(hostId);
      int countOfVmsOnHost = vmXenonBackend.countVmsOnHost(hostEntity);
      assertThat(countOfVmsOnHost, is(1));
    }

    @Test
    public void testToApiRepresentation() throws ExternalException {
      Vm foundVm = vmXenonBackend.toApiRepresentation(vmId);
      assertThat(foundVm.getName(), is(vm.name));

      assertThat(foundVm.getFlavor(), is(flavorEntity.getName()));
      assertThat(foundVm.getSourceImageId(), is(vm.imageId));
      assertThat(foundVm.getState(), is(vm.vmState));

      assertThat(foundVm.getAttachedIsos().get(0).getName(),
          is(vm.isos.get(0).getName()));
      assertThat(foundVm.getAttachedIsos().get(0).getSize(),
          is(vm.isos.get(0).getSize()));

      assertThat(foundVm.getMetadata().get("key1"),
          is(vm.metadata.get("key1")));

      assertThat(foundVm.getTags().containsAll(vm.tags), is(true));

      assertThat(foundVm.getHost(), is(vm.host));
      assertThat(foundVm.getDatastore(), is(vm.datastore));
      assertThat(foundVm.getProjectId(), is(vm.projectId));
    }
  }

  /**
   * Tests for creating VMs on physical network.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateVmOnPhysicalNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareVmCreate() throws Throwable {
      String vmId = createdVmTaskEntity.getEntityId();
      assertThat(createdVmTaskEntity.getSteps().size(), is(2));
      assertThat(createdVmTaskEntity.getSteps().get(0).getOperation(),
          is(com.vmware.photon.controller.api.Operation.RESERVE_RESOURCE));
      assertThat(createdVmTaskEntity.getSteps().get(0).getTransientResourceEntities(ProjectEntity.KIND).size(), is(1));
      assertThat(createdVmTaskEntity.getSteps().get(0).getTransientResourceEntities(ProjectEntity.KIND).get(0).getId(),
          is(projectId));
      assertThat(createdVmTaskEntity.getSteps().get(1).getOperation(),
          is(com.vmware.photon.controller.api.Operation.CREATE_VM));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().get(0).getId(), is(vmId));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().get(0).getKind(), is(Vm.KIND));

      VmEntity vm = vmXenonBackend.findById(vmId);
      assertThat(vm, is(notNullValue()));
      assertThat(getUsage("vm.cost"), is(1.0));
      assertThat(vm.getImageId(), is(imageId));

      assertThat(vm.getAffinities().get(0).getResourceId(), is("disk-id1"));
      assertThat(vm.getAffinities().get(0).getKind(), is("disk"));
      assertThat(vm.getAffinities().get(1).getResourceId(), is("disk-id2"));
      assertThat(vm.getAffinities().get(1).getKind(), is("disk"));

      Set<TagEntity> tags = vm.getTags();
      assertThat(tags.size(), is(2));
      TagEntity tag1 = new TagEntity();
      tag1.setValue("value1");
      TagEntity tag2 = new TagEntity();
      tag2.setValue("value2");
      assertTrue(tags.contains(tag1));
      assertTrue(tags.contains(tag2));

      assertThat(vmCreateSpec.getNetworks().equals(vm.getNetworks()), is(true));
    }

    private double getUsage(String key) throws Throwable {
      ProjectEntity projectEntity = projectXenonBackend.findById(projectId);
      String resourceTicketId = projectEntity.getResourceTicketId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketXenonBackend.findById(resourceTicketId);
      return resourceTicketEntity.getUsage(key).getValue();
    }
  }

  /**
   * Tests for creating VMs on virtual network.
   */
  @Guice(modules = {XenonBackendWithVirtualNetworkTestModule.class, TestModule.class})
  public static class CreateVmOnVirtualNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareVmCreate() throws Throwable {
      String vmId = createdVmTaskEntity.getEntityId();
      assertThat(createdVmTaskEntity.getSteps().size(), is(3));
      assertThat(createdVmTaskEntity.getSteps().get(0).getOperation(),
          is(com.vmware.photon.controller.api.Operation.RESERVE_RESOURCE));
      assertThat(createdVmTaskEntity.getSteps().get(0).getTransientResourceEntities(ProjectEntity.KIND).size(), is(1));
      assertThat(createdVmTaskEntity.getSteps().get(0).getTransientResourceEntities(ProjectEntity.KIND).get(0).getId(),
          is(projectId));
      assertThat(createdVmTaskEntity.getSteps().get(1).getOperation(),
          is(com.vmware.photon.controller.api.Operation.CREATE_VM));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().size(), is(1));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().get(0).getId(), is(vmId));
      assertThat(createdVmTaskEntity.getToBeLockedEntities().get(0).getKind(), is(Vm.KIND));
      assertThat(createdVmTaskEntity.getSteps().get(2).getOperation(),
          is(com.vmware.photon.controller.api.Operation.CONNECT_VM_SWITCH));

      VmEntity vm = vmXenonBackend.findById(vmId);
      assertThat(vm, is(notNullValue()));
      assertThat(getUsage("vm.cost"), is(1.0));
      assertThat(vm.getImageId(), is(imageId));

      assertThat(vm.getAffinities().get(0).getResourceId(), is("disk-id1"));
      assertThat(vm.getAffinities().get(0).getKind(), is("disk"));
      assertThat(vm.getAffinities().get(1).getResourceId(), is("disk-id2"));
      assertThat(vm.getAffinities().get(1).getKind(), is("disk"));

      Set<TagEntity> tags = vm.getTags();
      assertThat(tags.size(), is(2));
      TagEntity tag1 = new TagEntity();
      tag1.setValue("value1");
      TagEntity tag2 = new TagEntity();
      tag2.setValue("value2");
      assertTrue(tags.contains(tag1));
      assertTrue(tags.contains(tag2));

      assertThat(vmCreateSpec.getNetworks().equals(vm.getNetworks()), is(true));
    }

    private double getUsage(String key) throws Throwable {
      ProjectEntity projectEntity = projectXenonBackend.findById(projectId);
      String resourceTicketId = projectEntity.getResourceTicketId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketXenonBackend.findById(resourceTicketId);
      return resourceTicketEntity.getUsage(key).getValue();
    }
  }

  /**
   * Tests for preparing vm deletion on physical network.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteVmOnPhysicalNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    private String vmId;

    private VmEntity vm;

    private String isoName = "iso-name";

    @Mock
    private InputStream inputStream;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);

      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareVmDelete() throws Throwable {
      TaskEntity task = vmXenonBackend.prepareVmDelete(vmId);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.DELETE_VM));
      assertThat(task.getToBeLockedEntities().size(), is(1));
      assertThat(task.getToBeLockedEntities().get(0).getId(), is(vmId));
      assertThat(task.getToBeLockedEntities().get(0).getKind(), is(Vm.KIND));
    }
  }

  /**
   * Tests for preparing vm deletion on virtual network.
   */
  @Guice(modules = {XenonBackendWithVirtualNetworkTestModule.class, TestModule.class})
  public static class DeleteVmOnVirtualNetworkTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    private String vmId;

    private VmEntity vm;

    private String isoName = "iso-name";

    @Mock
    private InputStream inputStream;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);

      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareVmDelete() throws Throwable {
      TaskEntity task = vmXenonBackend.prepareVmDelete(vmId);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(2));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.DELETE_VM));
      assertThat(task.getSteps().get(1).getOperation(),
          is(com.vmware.photon.controller.api.Operation.DISCONNECT_VM_SWITCH));
      assertThat(task.getSteps().get(1).getTransientResource(ResourceReserveStepCmd.VM_ID), is(vmId));
      assertThat(task.getSteps().get(1).getTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID),
          is(vm.getNetworks().get(0)));

      assertThat(task.getToBeLockedEntities().size(), is(1));
      assertThat(task.getToBeLockedEntities().get(0).getId(), is(vmId));
      assertThat(task.getToBeLockedEntities().get(0).getKind(), is(Vm.KIND));
    }
  }

  /**
   * Tests for API that generate VM operations related tasks only.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PrepareVmTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    private String vmId;

    private VmEntity vm;

    private String isoName = "iso-name";

    @Mock
    private InputStream inputStream;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);

      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testPrepareVmOperation() throws Throwable {
      com.vmware.photon.controller.api.Operation operation = VmOperation.VALID_OPERATIONS.iterator().next();
      TaskEntity task = vmXenonBackend.prepareVmOperation(vmId, operation);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(operation));

      try {
        vmXenonBackend.prepareVmOperation(vmId, com.vmware.photon.controller.api.Operation.MOCK_OP);
        fail("vmXenonBackend.prepareVmOperation with invalid operation should have failed");
      } catch (NotImplementedException e) {
        // do nothing
      }
    }

    @Test
    public void testPrepareVmDiskOperation() throws Throwable {
      com.vmware.photon.controller.api.Operation operation = com.vmware.photon.controller.api.Operation.DETACH_DISK;

      DiskService.State diskState = new DiskService.State();
      diskState.name = "test-vm-disk-1";
      diskState.projectId = projectId;
      diskState.flavorId = flavorXenonBackend.getEntityByNameAndKind("core-100", PersistentDisk.KIND).getId();
      diskState.capacityGb = 64;
      diskState.diskType = DiskType.PERSISTENT;
      diskState.state = DiskState.ATTACHED;

      Operation result = xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);
      DiskService.State createdDiskState = result.getBody(DiskService.State.class);
      String diskId = ServiceUtils.getIDFromDocumentSelfLink(createdDiskState.documentSelfLink);

      List<String> disks = new ArrayList<>();
      disks.add(diskId);
      TaskEntity task = vmXenonBackend.prepareVmDiskOperation(
          vmId, disks, operation);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(operation));

      try {
        vmXenonBackend.prepareVmDiskOperation(
            vmId, disks, com.vmware.photon.controller.api.Operation.MOCK_OP);
        fail("vmXenonBackend.prepareVmDiskOperation with invalid operation should have failed");
      } catch (NotImplementedException e) {
        // do nothing
      }
    }

    @Test
    public void testPrepareVmDiskOperationInvalidDisk() throws Throwable {
      com.vmware.photon.controller.api.Operation operation = com.vmware.photon.controller.api.Operation.DETACH_DISK;

      DiskService.State diskState = new DiskService.State();
      diskState.name = "test-vm-disk-1";
      diskState.projectId = "invalid-project";
      diskState.flavorId = flavorXenonBackend.getEntityByNameAndKind("core-100", PersistentDisk.KIND).getId();
      diskState.capacityGb = 64;
      diskState.diskType = DiskType.PERSISTENT;
      diskState.state = DiskState.DETACHED;

      Operation result = xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);
      DiskService.State createdDiskState = result.getBody(DiskService.State.class);
      String diskId = ServiceUtils.getIDFromDocumentSelfLink(createdDiskState.documentSelfLink);

      List<String> disks = new ArrayList<>();
      disks.add(diskId);

      try {
        vmXenonBackend.prepareVmDiskOperation(
            vmId, disks, com.vmware.photon.controller.api.Operation.ATTACH_DISK);
        fail("vmXenonBackend.prepareVmDiskOperation with invalid disk should have failed InvalidAttachDisksException");
      } catch (InvalidAttachDisksException e) {
        assertThat(e.getMessage(), is("Disk " + diskId + " and Vm " + vmId +
            " are not in the same project, can not attach."));
      }
    }

    @Test(expectedExceptions = InvalidImageStateException.class)
    public void testNullImageSizeThrowsInvalidImageStateException() throws Throwable {
      AttachedDiskCreateSpec disk = new AttachedDiskCreateSpec();
      disk.setBootDisk(true);
      ImageEntity image = new ImageEntity();
      image.setSize(null);
      List<Throwable> warnings = new ArrayList<>();
      VmXenonBackend.updateBootDiskCapacity(Arrays.asList(disk), image, warnings);
    }

    @DataProvider(name = "IsoFileNames")
    public Object[][] getIsoNames() {
      return new Object[][]{
          {isoName},
          {"/tmp/" + isoName},
          {"tmp/" + isoName}
      };
    }

    @Test(dataProvider = "IsoFileNames")
    public void testPrepareVmAttachIso(String isoFileName) throws Throwable {
      TaskEntity task = vmXenonBackend.prepareVmAttachIso(vmId, inputStream, isoFileName);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(2));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.UPLOAD_ISO));
      IsoEntity iso = (IsoEntity) task.getSteps().get(0).getTransientResourceEntities().get(1);
      assertThat(iso.getName(), is(isoName));

      assertThat(task.getToBeLockedEntities().size(), is(2));
      assertThat(task.getToBeLockedEntities().get(0).getId(), is(iso.getId()));
      assertThat(task.getToBeLockedEntities().get(0).getKind(), is(Iso.KIND));
      assertThat(task.getToBeLockedEntities().get(1).getId(), is(vmId));
      assertThat(task.getToBeLockedEntities().get(1).getKind(), is(Vm.KIND));
    }

    @Test
    public void testPrepareVmDetachIso() throws Throwable {
      TaskEntity task = vmXenonBackend.prepareVmDetachIso(vmId);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.DETACH_ISO));
      assertThat(task.getSteps().get(0).getTransientResourceEntities().get(0), is(vm));
    }

    @Test
    public void testPrepareVmGetNetworks() throws Throwable {
      TaskEntity task = vmXenonBackend.prepareVmGetNetworks(vmId);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.GET_NETWORKS));
    }

    @Test
    public void testPrepareVmGetMksTicket() throws Throwable {
      vmXenonBackend.updateState(vmXenonBackend.findById(vmId), VmState.STARTED);
      TaskEntity task = vmXenonBackend.prepareVmGetMksTicket(vmId);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(1));
      assertThat(task.getSteps().get(0).getOperation(), is(com.vmware.photon.controller.api.Operation.GET_MKS_TICKET));
    }

    @Test
    public void testPrepareVmGetMksTicketInvalidVmState() throws Throwable {
      try {
        vmXenonBackend.prepareVmGetMksTicket(vmId);
      } catch (InvalidVmStateException e) {
        assertThat(e.getMessage(), is("Get Mks Ticket is not allowed on vm that is not powered on."));
      }
    }

    @Test
    public void testPrepareSetMetadata() throws Throwable {
      Map<String, String> metadata = new HashMap<>();
      metadata.put("key", "value");

      TaskEntity task = vmXenonBackend.prepareSetMetadata(vmId, metadata);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.COMPLETED));
      assertThat(task.getSteps().size(), is(0));

      // check that metadata was saved
      VmEntity updatedVm = vmXenonBackend.findById(vmId);
      assertThat(updatedVm, notNullValue());
      assertThat(updatedVm.getMetadata(), is(metadata));

      // make sure that no other fields have changed
      updatedVm.setMetadata(this.vm.getMetadata());
      assertThat(this.vm, is(updatedVm));
    }

    @Test(dataProvider = "vmCreateImageReplicationType")
    public void testPrepareVmCreateImage(ImageReplicationType replicationType) throws Throwable {
      ImageCreateSpec imageCreateSpec = new ImageCreateSpec();
      imageCreateSpec.setName("i1");
      imageCreateSpec.setReplicationType(replicationType);

      TaskEntity task = vmXenonBackend.prepareVmCreateImage(vmId, imageCreateSpec);

      assertThat(task, is(notNullValue()));
      assertThat(task.getState(), is(TaskEntity.State.QUEUED));
      assertThat(task.getSteps().size(), is(2));
      StepEntity step = task.getSteps().get(0);
      assertThat(step.getOperation(), is(com.vmware.photon.controller.api.Operation.CREATE_VM_IMAGE));
      assertThat(step.getTransientResourceEntities().size(), is(3));
      assertThat(step.getTransientResourceEntities(Vm.KIND).size(), is(1));
      assertThat(step.getTransientResourceEntities(Image.KIND).size(), is(2));

      assertThat(step.getTransientResourceEntities(Vm.KIND).get(0).getId(), is(vm.getId()));

      ImageEntity image = (ImageEntity) step.getTransientResourceEntities(ImageEntity.KIND).get(0);
      assertThat(image.getName(), is(imageCreateSpec.getName()));
      assertThat(image.getReplicationType(), is(imageCreateSpec.getReplicationType()));
      assertThat(image.getState(), is(ImageState.CREATING));
      assertThat(image.getSize(), is(createdImageState.size));
      assertThat(image.getImageSettingsMap(), is((Map<String, String>) ImmutableMap.of("n1", "v1", "n2", "v2")));

      ImageEntity vmImage = (ImageEntity) step.getTransientResourceEntities(ImageEntity.KIND).get(1);
      assertThat(vmImage.getId(), is(vm.getImageId()));

      step = task.getSteps().get(1);
      assertThat(step.getOperation(), is(com.vmware.photon.controller.api.Operation.REPLICATE_IMAGE));
    }

    @DataProvider(name = "vmCreateImageReplicationType")
    public Object[][] getVmCreateImageReplicationType() {
      return new Object[][]{
          {ImageReplicationType.EAGER},
          {ImageReplicationType.ON_DEMAND}
      };
    }

    @Test
    public void testAddIso() throws Throwable {
      IsoEntity isoEntity = new IsoEntity();
      isoEntity.setId("iso-id");
      isoEntity.setName("iso-name");
      isoEntity.setSize(100L);

      vmXenonBackend.addIso(isoEntity, vm);

      VmEntity updatedVm = vmXenonBackend.findById(vmId);
      assertThat(updatedVm, CoreMatchers.notNullValue());
      assertThat(updatedVm.getIsos().size(), is(1));
      assertThat(updatedVm.getIsos().get(0), is(isoEntity));
    }

    @Test(expectedExceptions = VmNotFoundException.class,
        expectedExceptionsMessageRegExp = "VM nonExistingVm not found")
    public void testAddIsoFailed() throws Throwable {
      VmEntity vmEntity = new VmEntity();
      vmEntity.setId("nonExistingVm");
      vmXenonBackend.addIso(new IsoEntity(), vmEntity);
    }
  }

  /**
   * Tests for tombstone API.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class TombstoneVmTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @Inject
    private TombstoneXenonBackend tombstoneXenonBackend;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    private String vmId;

    private VmEntity vm;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);

      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testTombstone() throws Throwable {
      TombstoneEntity tombstone = tombstoneXenonBackend.getByEntityId(vm.getId());
      assertThat(tombstone, nullValue());
      assertThat(getUsage("vm.cost"), is(1.0));

      vmXenonBackend.tombstone(vm);

      tombstone = tombstoneXenonBackend.getByEntityId(vm.getId());
      assertThat(tombstone.getEntityId(), is(vm.getId()));
      assertThat(tombstone.getEntityKind(), is(Vm.KIND));
      assertThat(getUsage("vm.cost"), is(0.0));
    }

    @Test(enabled = false)
    public void testTombstoneDeletesNetworksInPendingDelete() throws Throwable {
      TombstoneEntity tombstone = tombstoneXenonBackend.getByEntityId(vm.getId());
      assertThat(tombstone, nullValue());
      assertThat(getUsage("vm.cost"), is(1.0));

      // delete a network
      String networkToDelete = vm.getNetworks().get(0);
      networkXenonBackend.prepareNetworkDelete(networkToDelete);
      assertThat(tombstoneXenonBackend.getByEntityId(networkToDelete), nullValue());

      vmXenonBackend.tombstone(vm);

      tombstone = tombstoneXenonBackend.getByEntityId(vm.getId());
      assertThat(tombstone.getEntityId(), is(vm.getId()));
      assertThat(tombstone.getEntityKind(), is(Vm.KIND));
      assertThat(getUsage("vm.cost"), is(0.0));

      tombstone = tombstoneXenonBackend.getByEntityId(networkToDelete);
      assertThat(tombstone.getEntityId(), is(tombstone.getId()));
      assertThat(tombstone.getEntityKind(), is(Network.KIND));
    }

    private double getUsage(String key) throws Throwable {
      ProjectEntity projectEntity = projectXenonBackend.findById(projectId);
      String resourceTicketId = projectEntity.getResourceTicketId();
      ResourceTicketEntity resourceTicketEntity = resourceTicketXenonBackend.findById(resourceTicketId);
      return resourceTicketEntity.getUsage(key).getValue();
    }
  }

  /**
   * Tests for vm patch related API.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class PatchVmTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private VmXenonBackend vmXenonBackend;

    @Inject
    private TenantXenonBackend tenantXenonBackend;

    @Inject
    private ResourceTicketXenonBackend resourceTicketXenonBackend;

    @Inject
    private ProjectXenonBackend projectXenonBackend;

    @Inject
    private FlavorXenonBackend flavorXenonBackend;

    @Inject
    private EntityLockXenonBackend entityLockXenonBackend;

    @Inject
    private FlavorLoader flavorLoader;

    @Inject
    private NetworkXenonBackend networkXenonBackend;

    private String vmId;

    private VmEntity vm;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
      commonDataSetup(
          tenantXenonBackend,
          resourceTicketXenonBackend,
          projectXenonBackend,
          flavorXenonBackend,
          flavorLoader);

      commonVmAndImageSetup(vmXenonBackend, networkXenonBackend);

      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testUpdateState() throws Throwable {
      assertThat(vm.getState(), is(not(VmState.ERROR)));
      vmXenonBackend.updateState(vm, VmState.ERROR);
      assertThat(vm.getState(), is(VmState.ERROR));
      vm = vmXenonBackend.findById(vmId);
      assertThat(vm.getState(), is(VmState.ERROR));

      String agent = UUID.randomUUID().toString();
      String agentIp = UUID.randomUUID().toString();
      String datastoreId = UUID.randomUUID().toString();
      String datastoreName = UUID.randomUUID().toString();

      vmXenonBackend.updateState(vm, VmState.STARTED, agent, agentIp, datastoreId, datastoreName);
      assertThat(vm.getState(), is(VmState.STARTED));
      assertThat(vm.getAgent(), is(agent));
      assertThat(vm.getHost(), is(agentIp));
      assertThat(vm.getDatastore(), is(datastoreId));
      assertThat(vm.getDatastoreName(), is(datastoreName));
      vm = vmXenonBackend.findById(vmId);
      assertThat(vm.getState(), is(VmState.STARTED));
      assertThat(vm.getAgent(), is(agent));
      assertThat(vm.getHost(), is(agentIp));
      assertThat(vm.getDatastore(), is(datastoreId));
      assertThat(vm.getDatastoreName(), is(datastoreName));
    }

    @Test
    public void testDetachIso() throws Throwable {
      VmService.State vmState = new VmService.State();
      Iso iso = new Iso();
      iso.setName(UUID.randomUUID().toString());
      vmState.isos = new ArrayList<>();
      vmState.isos.add(iso);
      xenonClient.patch(VmServiceFactory.SELF_LINK + "/" + vm.getId(), vmState);
      assertThat(vmXenonBackend.isosAttached(vm).isEmpty(), is(false));
      vmXenonBackend.detachIso(vm);
      assertThat(vmXenonBackend.isosAttached(vm).isEmpty(), is(true));
    }

    @Test
    public void testAddtag() throws Throwable {
      vm = vmXenonBackend.findById(vm.getId());
      int originalSize = vm.getTags().size();
      Tag tag = new Tag("namespace:predicate=value");
      vmXenonBackend.addTag(vm.getId(), tag);
      vm = vmXenonBackend.findById(vm.getId());
      assertThat(vm.getTags().size(), is(originalSize + 1));
      List<String> tagValues = vm.getTags().stream().map(t -> t.getValue()).collect(Collectors.toList());
      assertTrue(tagValues.contains(tag.getValue()));
    }

    @Test
    public void testAddTagWhenOriginalTagIsNull() throws Throwable {
      //overwriting the vmCreateSpec tags to empty from non-empty
      vmCreateSpec.setTags(null);

      createdVmTaskEntity = vmXenonBackend.prepareVmCreate(projectId, vmCreateSpec);
      vmId = createdVmTaskEntity.getEntityId();
      entityLockXenonBackend.clearTaskLocks(createdVmTaskEntity);
      vm = vmXenonBackend.findById(vmId);
      int originalSize = vm.getTags().size();
      Tag tag = new Tag("namespace:predicate=value");
      vmXenonBackend.addTag(vm.getId(), tag);
      vm = vmXenonBackend.findById(vm.getId());
      assertThat(vm.getTags().size(), is(1));
      List<String> tagValues = vm.getTags().stream().map(t -> t.getValue()).collect(Collectors.toList());
      assertTrue(tagValues.contains(tag.getValue()));
    }
  }
}
