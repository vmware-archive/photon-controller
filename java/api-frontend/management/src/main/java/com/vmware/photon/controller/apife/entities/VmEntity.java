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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;
import com.vmware.photon.controller.apife.exceptions.external.MoreThanOneHostAffinityException;

import com.google.common.base.Objects;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VM entity.
 */
@Entity(name = "Vm")
@NamedQueries({
    @NamedQuery(
        name = "Vm.findAll",
        query = "SELECT vm FROM Vm vm WHERE vm.projectId = :projectId"
    ),
    @NamedQuery(
        name = "Vm.listAll",
        query = "SELECT vm FROM Vm vm"
    ),
    @NamedQuery(
        name = "Vm.findByName",
        query = "SELECT vm FROM Vm vm WHERE vm.name = :name AND vm.projectId = :projectId"
    ),
    @NamedQuery(
        name = "Vm.findByTag",
        query = "SELECT vm FROM Vm vm INNER JOIN vm.tags tag" +
            " WHERE tag.value = :value AND vm.projectId = :projectId"
    ),
    @NamedQuery(
        name = "Vm.findByFlavor",
        query = "SELECT vm FROM Vm vm WHERE vm.flavorId = :flavorId"
    ),
    @NamedQuery(
        name = "Vm.findByImage",
        query = "SELECT vm FROM Vm vm " +
            "WHERE vm.imageId = :imageId "
    ),
    @NamedQuery(
        name = "Vm.listAllByHostIp",
        query = "SELECT vm FROM Vm vm WHERE vm.host = :hostIp"
    ),
    @NamedQuery(
        name = "Vm.countVmsByHostIp",
        query = "SELECT COUNT (vm) FROM Vm vm WHERE vm.host = :hostIp"
    )
})
public class VmEntity extends InfrastructureEntity {

  @Enumerated(EnumType.STRING)
  private VmState state;

  @Transient
  private List<AttachedDiskEntity> attachedDisks = new ArrayList<>();

  @OneToMany(mappedBy = "vm", fetch = FetchType.LAZY)
  @Cascade(CascadeType.ALL)
  private List<LocalityEntity> affinities = new ArrayList<>();

  private String imageId;

  @OneToMany(mappedBy = "vm", fetch = FetchType.LAZY)
  private List<IsoEntity> isos = new ArrayList<>();

  @NotNull
  @ElementCollection(fetch = FetchType.EAGER)
  @Cascade(CascadeType.ALL)
  private Map<String, String> metadata = new HashMap<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @Cascade(CascadeType.ALL)
  private List<String> networks = new ArrayList<>();

  private String agent;

  private String defaultGateway;

  private String host;
  /**
   * When this property is set, the VM is created
   * to the infrastructure. The various backend components (VmBackend, etc,)
   * own this property and use this to manage the lazy creation of Vms.
   * <p>
   * For example, during Disk creation, the DiskCreateSpec has an affinity field
   * which indicates which infrastructure or its affinity that Disk should be associated to.
   */
  private String datastore;

  private String datastoreName;

  @Transient
  private Map<String, String> environment = new HashMap<>();

  /**
   * Warnings related to vm entity.
   */
  @Transient
  private List<Throwable> warnings = new ArrayList<>();

  @Override
  public String getKind() {
    return Vm.KIND;
  }

  public List<AttachedDiskEntity> getAttachedDisks() {
    return attachedDisks;
  }

  public void setAttachedDisks(List<AttachedDiskEntity> attachedDisks) {
    this.attachedDisks = attachedDisks;
  }

  public void removeAttachedDisks(List<AttachedDiskEntity> attachedDisks) {
    for (AttachedDiskEntity attachedDisk : attachedDisks) {
      removeAttachedDisk(attachedDisk);
    }
  }

  public void addAttachedDisk(AttachedDiskEntity attachedDisk) {
    attachedDisks.add(attachedDisk);
    attachedDisk.setVmId(this.getId());
  }

  public void removeAttachedDisk(AttachedDiskEntity attachedDisk) {
    attachedDisks.remove(attachedDisk);
    attachedDisk.setVmId(null);
  }

  public List<LocalityEntity> getAffinities() {
    return affinities;
  }

  public void setAffinities(List<LocalityEntity> affinities) {
    this.affinities = affinities;
  }

  public List<String> getAffinities(String kind) {
    List<String> results = new ArrayList<>();
    if (affinities == null) {
      return results;
    }

    for (LocalityEntity affinity : affinities) {
      if (java.util.Objects.equals(affinity.getKind(), kind)) {
        results.add(affinity.getResourceId());
      }
    }

    return results;
  }

  public String getHostAffinity() throws MoreThanOneHostAffinityException {
    if (affinities == null) {
      return null;
    }
    boolean isHostAffinityPresent = false;
    String hostAffinity = null;

    for (LocalityEntity affinity : affinities) {
      if (java.util.Objects.equals(affinity.getKind(), Host.KIND)) {
        if (isHostAffinityPresent) {
          throw new MoreThanOneHostAffinityException();
        }
        hostAffinity = affinity.getResourceId();
        isHostAffinityPresent = true;
      }
    }
    return hostAffinity;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public List<String> getNetworks() {
    return networks;
  }

  public void setNetworks(List<String> networks) {
    this.networks = networks;
  }

  public String getAgent() {
    return agent;
  }

  public void setAgent(String agent) {
    this.agent = agent;
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public VmState getState() {
    return state;
  }

  public void setState(VmState state) {
    this.state = state;
  }

  public String getDefaultGateway() {
    return defaultGateway;
  }

  public void setDefaultGateway(String defaultGateway) {
    this.defaultGateway = defaultGateway;
  }

  public String getDatastore() {
    return datastore;
  }

  public void setDatastore(String datastore) {
    this.datastore = datastore;
  }

  public String getDatastoreName() {
    return datastoreName;
  }

  public void setDatastoreName(String datastoreName) {
    this.datastoreName = datastoreName;
  }

  public String getImageId() {
    return this.imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public List<IsoEntity> getIsos() {
    return isos;
  }

  public void setIsos(List<IsoEntity> isos) {
    this.isos = isos;
  }

  public void addIso(IsoEntity iso) {
    isos.add(iso);
  }

  public void removeIso(IsoEntity iso) {
    isos.remove(iso);
  }

  public List<Throwable> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<Throwable> warnings) {
    this.warnings = warnings;
  }

  public void addWarning(Throwable warning) {
    this.warnings.add(warning);
  }

  @Override
  protected Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("state", state)
        .add("agent", agent);
  }

  /**
   * Construct the Vm folder path.
   * [datastore1] vms/vm/vmId
   *
   * @return
   */
  public String buildVmFolderPath() {
    return String.format("[%s] vms/%s/%s", datastoreName, getId().substring(0, 2), getId());
  }
}
