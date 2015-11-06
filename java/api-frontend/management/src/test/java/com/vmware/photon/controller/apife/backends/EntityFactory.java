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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.AttachedDiskDao;
import com.vmware.photon.controller.apife.db.dao.DeploymentDao;
import com.vmware.photon.controller.apife.db.dao.EphemeralDiskDao;
import com.vmware.photon.controller.apife.db.dao.ImageDao;
import com.vmware.photon.controller.apife.db.dao.ImageSettingsDao;
import com.vmware.photon.controller.apife.db.dao.PersistentDiskDao;
import com.vmware.photon.controller.apife.db.dao.PortGroupDao;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.ImageSettingsEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.PortGroupEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Test helper for creating entities. It just creates objects in DB, with no infrastructure side effects,
 * so it's encouraged to used instead of XxxBackend classes to populate the database.
 */
@Singleton
public class EntityFactory {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private EphemeralDiskDao ephemeralDiskDao;

  @Inject
  private PersistentDiskDao persistentDiskDao;

  @Inject
  private AttachedDiskDao attachedDiskDao;

  @Inject
  private ResourceTicketDao resourceTicketDao;

  @Inject
  private PortGroupDao portGroupDao;

  @Inject
  private DiskBackend diskBackend;

  @Inject
  private VmDao vmDao;

  @Inject
  private ImageDao imageDao;

  @Inject
  private ImageSettingsDao imageSettingsDao;

  @Inject
  private DeploymentDao deploymentDao;

  @Inject
  private FlavorLoader flavorLoader;

  @Inject
  private FlavorBackend flavorBackend;

  @Inject
  private NetworkBackend networkBackend;

  @Inject
  private ResourceTicketBackend resourceTicketBackend;

  public static DeploymentEntity buildDeploymentEntity(
      DeploymentState deploymentState, boolean authEnabled,
      String oauthEndpoint, Integer port, String oauthTenant, String oauthUsername,
      String oauthPassword, List<String> oauthSecurityGroups, String ntpEndpoint,
      String syslogEndpoint, String imageDatastore, boolean useImageDatastoreForVms) {
    DeploymentEntity deployment = new DeploymentEntity();
    deployment.setState(deploymentState);
    deployment.setAuthEnabled(authEnabled);
    deployment.setOauthEndpoint(oauthEndpoint);
    deployment.setOauthPort(port);
    deployment.setOauthTenant(oauthTenant);
    deployment.setOauthUsername(oauthUsername);
    deployment.setOauthPassword(oauthPassword);
    deployment.setOauthSecurityGroups(oauthSecurityGroups);
    deployment.setNtpEndpoint(ntpEndpoint);
    deployment.setSyslogEndpoint(syslogEndpoint);
    deployment.setImageDatastore(imageDatastore);
    deployment.setUseImageDatastoreForVms(useImageDatastoreForVms);
    return deployment;
  }

  public void setFlavorBackend(FlavorBackend flavorBackend) {
    this.flavorBackend = flavorBackend;
  }

  public FlavorBackend getFlavorBackend() {
    return flavorBackend;
  }

  @Transactional
  public TenantEntity createTenant(String name) {
    TenantEntity tenant = new TenantEntity();
    tenant.setName(name);
    tenantDao.create(tenant);
    return tenant;
  }

  @Transactional
  public String createTenantResourceTicket(String tenantId, String name, QuotaLineItemEntity... limits) {
    ResourceTicketEntity ticket = new ResourceTicketEntity();
    ticket.setName(name);
    ticket.setLimits(Lists.newArrayList(limits));
    ticket.setTenantId(tenantId);

    resourceTicketDao.create(ticket);
    return ticket.getId();
  }

  @Transactional
  public String createProject(String tenantId, String ticketId, String name, QuotaLineItemEntity... limits)
      throws Exception {
    TenantEntity tenant = tenantDao.findById(tenantId).get();
    ResourceTicketEntity tenantTicket = resourceTicketDao.findById(ticketId).get();
    checkState(tenantTicket.getTenantId().equals(tenant.getId()));

    List<QuotaLineItemEntity> cost = Lists.newArrayList(limits);
    ResourceTicketEntity projectTicket = resourceTicketBackend.subdivide(ticketId, cost);

    ProjectEntity project = new ProjectEntity();
    project.setName(name);
    project.setTenantId(tenantId);
    project.setResourceTicketId(projectTicket.getId());
    projectDao.create(project);

    return project.getId();
  }

  @Transactional
  public VmEntity createVm(String projectId, String flavor, String name, VmState state, ImageEntity image,
                           IsoEntity isoEntity)
      throws ExternalException {
    ProjectEntity project = projectDao.findById(projectId).get();
    VmEntity vm = new VmEntity();
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(flavor, Vm.KIND);
    vm.setFlavorId(flavorEntity.getId());
    vm.setName(name);
    vm.setCost(flavorLoader.getCost(flavorEntity));
    vm.setState(state);
    vm.setAgent("agent-id");
    if (image != null) {
      vm.setImageId(image.getId());
    }
    if (isoEntity != null) {
      vm.addIso(isoEntity);
    }

    vm.setProjectId(project.getId());
    vmDao.create(vm);

    String resourceTicketId = project.getResourceTicketId();
    resourceTicketBackend.consumeQuota(resourceTicketId, new QuotaCost(vm.getCost()));

    return vm;
  }

  public VmEntity createVm(String projectId, String flavor, String name)
      throws ExternalException {
    return createVm(projectId, flavor, name, VmState.STOPPED, null, null);
  }

  public VmEntity createVm(String projectId, String flavor, String name, VmState state)
      throws ExternalException {
    return createVm(projectId, flavor, name, state, null, null);
  }

  public VmEntity createVm(String projectId, String flavor, String name, VmState state, ImageEntity image)
      throws ExternalException {
    return createVm(projectId, flavor, name, state, image, null);
  }

  @Transactional
  public DeploymentEntity createDeployment(
      DeploymentState deploymentState,
      boolean authEnabled,
      String oauthEndpoint,
      Integer port,
      String oauthTenant,
      String oauthUsername,
      String oauthPassword,
      List<String> oauthSecurityGroups,
      String ntpEndpoint,
      String syslogEndpoint,
      String imageDatastore,
      boolean useImageDatastoreForVms) {
    DeploymentEntity deployment = buildDeploymentEntity(
        deploymentState, authEnabled, oauthEndpoint, port, oauthTenant, oauthUsername,
        oauthPassword, oauthSecurityGroups, ntpEndpoint, syslogEndpoint, imageDatastore,
        useImageDatastoreForVms);

    return deploymentDao.create(deployment);
  }

  public DeploymentEntity createDeployment() {
    return createDeployment(DeploymentState.CREATING,
        true, "192.168.0.1", 443, "t", "u", "p", Arrays.asList(new String[]{"adminGroup1", "adminGroup2"}),
        "ntp", "syslog", "imageDatastore", true);
  }

  @Transactional
  public PersistentDiskEntity createPersistentDisk(String projectId, String flavor, String name, int capacityGb)
      throws ExternalException {
    ProjectEntity project = projectDao.findById(projectId).get();
    PersistentDiskEntity disk = new PersistentDiskEntity();
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(flavor, PersistentDisk.KIND);
    disk.setFlavorId(flavorEntity.getId());
    disk.setName(name);
    disk.setCapacityGb(capacityGb);

    List<QuotaLineItemEntity> cost = flavorLoader.getCost(flavorEntity);
    List<QuotaLineItemEntity> enhancedCost = new ArrayList<>(cost);
    String capacityKey = PersistentDisk.KIND + ".capacity";
    QuotaLineItemEntity capacity = new QuotaLineItemEntity(capacityKey, capacityGb, QuotaUnit.GB);
    for (QuotaLineItemEntity qli : enhancedCost) {

      // assert/crash if capacity key is present in a disk entity's static cost
      // this is computed in this code at runtime.
      if (qli.getKey().equals(capacityKey)) {
        checkState(!qli.getKey().equals(capacityKey));
      }
    }
    enhancedCost.add(capacity);
    disk.setCost(enhancedCost);

    disk.setProjectId(projectId);

    persistentDiskDao.create(disk);

    String resourceTicketId = project.getResourceTicketId();
    resourceTicketBackend.consumeQuota(resourceTicketId, new QuotaCost(disk.getCost()));
    return disk;
  }

  @Transactional
  public EphemeralDiskEntity createEphemeralDisk(String projectId, String flavor, String name, int capacityGb)
      throws ExternalException {
    ProjectEntity project = projectDao.findById(projectId).get();
    EphemeralDiskEntity disk = new EphemeralDiskEntity();
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(flavor, EphemeralDisk.KIND);
    disk.setFlavorId(flavorEntity.getId());
    disk.setName(name);
    disk.setCapacityGb(capacityGb);

    List<QuotaLineItemEntity> enhancedCost = new ArrayList<>(flavorLoader.getCost(flavorEntity));
    String capacityKey = EphemeralDisk.KIND + ".capacity";
    QuotaLineItemEntity capacity = new QuotaLineItemEntity(capacityKey, capacityGb, QuotaUnit.GB);
    for (QuotaLineItemEntity qli : enhancedCost) {

      // assert/crash if capacity key is present in a disk entity's static cost
      // this is computed in this code at runtime.
      if (qli.getKey().equals(capacityKey)) {
        checkState(!qli.getKey().equals(capacityKey));
      }
    }
    enhancedCost.add(capacity);
    disk.setCost(enhancedCost);

    disk.setProjectId(projectId);

    ephemeralDiskDao.create(disk);

    String resourceTicketId = project.getResourceTicketId();
    resourceTicketBackend.consumeQuota(resourceTicketId, new QuotaCost(disk.getCost()));

    return disk;
  }

  @Transactional
  public String attachDisk(VmEntity vm, String diskKind, String diskId)
      throws DiskNotFoundException {
    return attachDisk(vm, diskKind, diskId, DiskState.ATTACHED);
  }

  @Transactional
  public String attachDisk(VmEntity vm, String diskKind, String diskId, DiskState state)
      throws DiskNotFoundException {
    BaseDiskEntity disk = diskBackend.find(diskKind, diskId);

    AttachedDiskEntity attachedDisk = new AttachedDiskEntity();
    attachedDisk.setUnderlyingDiskIdAndKind(disk);
    disk.setState(state);

    vm.addAttachedDisk(attachedDisk);

    attachedDiskDao.create(attachedDisk);
    return attachedDisk.getId();
  }

  @Transactional
  public double getProjectUsage(String projectId, String key) {
    ProjectEntity projectEntity = projectDao.findById(projectId).get();
    ResourceTicketEntity resourceTicketEntity = resourceTicketDao.findById(projectEntity.getResourceTicketId()).get();
    QuotaLineItemEntity quotaLineItemEntity = resourceTicketEntity.getUsage(key);
    return quotaLineItemEntity.getValue();
  }

  @Transactional
  public FlavorEntity createFlavor(String name, String kind, List<QuotaLineItem> cost) throws ExternalException {
    FlavorCreateSpec flavor = new FlavorCreateSpec();
    flavor.setName(name);
    flavor.setKind(kind);

    flavor.setCost(cost);

    TaskEntity taskEntity = flavorBackend.createFlavor(flavor);

    return flavorBackend.getEntityById(taskEntity.getEntityId());
  }

  @Transactional
  public void loadFlavors() throws IOException, ExternalException {
    for (FlavorCreateSpec spec : flavorLoader.getAllFlavors()) {
      try {
        flavorBackend.getEntityByNameAndKind(spec.getName(), spec.getKind());
      } catch (FlavorNotFoundException ex) {
        List<QuotaLineItemEntity> costEntityList = new ArrayList<>();
        for (QuotaLineItem item : spec.getCost()) {
          costEntityList.add(new QuotaLineItemEntity(item.getKey(), item.getValue(), item.getUnit()));
        }
        flavorBackend.createFlavor(spec);
      }
    }
  }

  @Transactional
  public PortGroupEntity createPortGroup(String portGroupName, String startAddress, String endAddress,
                                         String subnetMask, String gateway, List<UsageTag> usageTags) {
    PortGroupEntity portGroupEntity = new PortGroupEntity();
    portGroupEntity.setPortGroupName(portGroupName);
    portGroupEntity.setStartAddress(startAddress);
    portGroupEntity.setEndAddress(endAddress);
    portGroupEntity.setSubnetMask(subnetMask);
    portGroupEntity.setGateway(gateway);
    portGroupEntity.setUsageTags(UsageTagHelper.serialize(usageTags));

    return portGroupDao.create(portGroupEntity);
  }

  @Transactional
  public ImageEntity createImage(String name, ImageState state,
                                 ImageReplicationType replicationType,
                                 Long size, String... imageSettings) {
    assert (imageSettings.length % 2 == 0);
    ImageEntity image = new ImageEntity();
    image.setName(name);
    image.setState(state);
    image.setReplicationType(replicationType);
    image.setSize(size);
    imageDao.create(image);

    ImageSettingsEntity imageSetting = new ImageSettingsEntity();
    for (int i = 0; i < imageSettings.length; i++) {

      if (i % 2 == 0) {
        imageSetting = new ImageSettingsEntity();
        imageSetting.setImage(image);
        imageSetting.setName(imageSettings[i]);
      } else {
        imageSetting.setDefaultValue(imageSettings[i]);
        imageSettingsDao.create(imageSetting);
        image.getImageSettings().add(imageSetting);
      }
    }

    imageDao.update(image);
    return image;
  }

  @Transactional
  public NetworkEntity createNetwork(String name, List<String> portGroups) throws ExternalException {
    if (null == portGroups) {
      portGroups = new LinkedList<>();
    }

    NetworkCreateSpec network = new NetworkCreateSpec();
    network.setName(name);
    network.setPortGroups(portGroups);

    TaskEntity taskEntity = networkBackend.createNetwork(network);
    return networkBackend.findById(taskEntity.getEntityId());
  }
}
