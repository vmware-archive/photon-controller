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
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.exceptions.external.MoreThanOneHostAffinityException;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link VmEntity}.
 */
public class VmEntityTest {

  private VmEntity vmEntity;

  @BeforeMethod
  public void setUp() {
    vmEntity = new VmEntity();
  }

  @Test
  public void testVmSuccessful() {
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId("project-1");

    ImageEntity image = new ImageEntity();
    image.setId("image-id");
    FlavorEntity flavorEntity = new FlavorEntity();
    flavorEntity.setId(UUID.randomUUID().toString());

    IsoEntity iso = new IsoEntity();
    iso.setId("iso-id");
    iso.setName("iso-name");
    iso.setSize(new Long(10000));

    vmEntity.setId("vm-id1");
    vmEntity.setName("vm-1");
    vmEntity.setProjectId(projectEntity.getId());
    vmEntity.setImageId(image.getId());
    vmEntity.setFlavorId(flavorEntity.getId());
    vmEntity.setAgent("agent-1");
    vmEntity.setHost("1.1.1.1");
    vmEntity.setDefaultGateway("gateway-1");
    vmEntity.setState(VmState.CREATING);
    vmEntity.setDatastore("datastore-1");
    vmEntity.addIso(iso);
    vmEntity.setUseVirtualNetwork(true);
    vmEntity.setNetworks(ImmutableList.of("network-id"));

    assertThat(vmEntity.getId(), is("vm-id1"));
    assertThat(vmEntity.getName(), is("vm-1"));

    assertThat(vmEntity.getProjectId(), is(projectEntity.getId()));

    assertThat(vmEntity.getFlavorId(), is(flavorEntity.getId()));
    assertThat(vmEntity.getImageId(), is("image-id"));
    assertThat(vmEntity.getAgent(), is("agent-1"));
    assertThat(vmEntity.getHost(), is("1.1.1.1"));
    assertThat(vmEntity.getDefaultGateway(), is("gateway-1"));
    assertThat(vmEntity.getState(), is(VmState.CREATING));
    assertThat(vmEntity.getIsos().get(0), is(iso));
    assertThat(vmEntity.isUseVirtualNetwork(), is(true));
    assertThat(vmEntity.getNetworks(), is(ImmutableList.of("network-id")));
  }

  @Test
  public void testVmFlavorNotSet() {
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId("project-1");

    vmEntity.setId("vm-id1");
    vmEntity.setName("vm-1");
    vmEntity.setProjectId(projectEntity.getId());
    vmEntity.setAgent("agent-1");
    vmEntity.setDefaultGateway("gateway-1");
    vmEntity.setState(VmState.CREATING);
    vmEntity.setDatastore("datastore-1");

    assertThat(vmEntity.getId(), is("vm-id1"));
    assertThat(vmEntity.getProjectId(), is(projectEntity.getId()));
    assertThat(vmEntity.getFlavorId(), is(nullValue()));
  }

  @Test
  public void testRemoveIso() {
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId("project-1");

    vmEntity.setId("vm-id1");
    vmEntity.setName("vm-1");
    vmEntity.setProjectId(projectEntity.getId());

    IsoEntity iso = new IsoEntity();
    iso.setId("iso-id");
    iso.setName("iso-name");
    iso.setSize(new Long(10000));

    vmEntity.addIso(iso);
    assertThat(vmEntity.getIsos().get(0), is(iso));

    vmEntity.removeIso(iso);
    assertThat(vmEntity.getIsos().isEmpty(), is(true));
  }

  @Test
  public void testGetAffinitiesWithKind() {
    VmEntity vm = new VmEntity();
    vm.setAffinities(null);
    assertThat(vm.getAffinities("vm").isEmpty(), is(true));

    vm.setAffinities(new ArrayList<LocalityEntity>());
    assertThat(vm.getAffinities("vm").isEmpty(), is(true));

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setKind("vm");
    localityEntity.setResourceId("vm-id1");
    List<LocalityEntity> affinities = ImmutableList.of(localityEntity);
    vm.setAffinities(affinities);

    assertThat(vm.getAffinities("disk").isEmpty(), is(true));
    assertThat(vm.getAffinities("vm"), is((List<String>) ImmutableList.of("vm-id1")));
  }

  @Test
  public void testGetHostAffinity() throws Throwable {
    VmEntity vm = new VmEntity();
    vm.setAffinities(null);
    assertThat(vm.getHostAffinity(), nullValue());

    vm.setAffinities(new ArrayList<LocalityEntity>());
    LocalityEntity localityEntity1 = new LocalityEntity();
    localityEntity1.setKind(Host.KIND);
    localityEntity1.setResourceId("host-id1");
    vm.setAffinities(ImmutableList.of(localityEntity1));

    assertThat(vm.getHostAffinity(), is("host-id1"));

    LocalityEntity localityEntity2 = new LocalityEntity();
    localityEntity2.setKind(Host.KIND);
    localityEntity2.setResourceId("host-id2");
    vm.setAffinities(ImmutableList.of(localityEntity1, localityEntity2));

    try {
      vm.getHostAffinity();
      fail("should have failed with MoreThanOneHostAffinityException");
    } catch (MoreThanOneHostAffinityException e) {
    }
  }
}
