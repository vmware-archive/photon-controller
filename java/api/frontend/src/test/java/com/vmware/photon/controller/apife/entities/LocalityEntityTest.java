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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link ResourceTicketEntity}.
 */
public class LocalityEntityTest {
  private LocalityEntity localityEntity;

  @BeforeMethod
  public void setUp() {
    localityEntity = new LocalityEntity();
  }

  @Test
  public void testLocalityEntity() {
    VmEntity vmEntity = new VmEntity();
    vmEntity.setId("vm-id1");
    vmEntity.setName("vm-1");

    PersistentDiskEntity disk = new PersistentDiskEntity();
    ProjectEntity projectEntity = new ProjectEntity();
    projectEntity.setId("project-1");

    String localityId = "vm-locality-1";
    localityEntity.setKind(vmEntity.getKind());
    localityEntity.setResourceId(localityId);
    localityEntity.setVm(vmEntity);
    localityEntity.setDisk(disk);

    assertThat(localityEntity.getResourceId(), is(localityId));
    assertThat(localityEntity.getKind(), is(vmEntity.getKind()));
    assertThat(localityEntity.getVm(), is(vmEntity));
  }

  @Test
  public void testNoLocality() {
    assertThat(localityEntity.getVm(), is(nullValue()));
    assertThat(localityEntity.getDisk(), is(nullValue()));
  }
}
