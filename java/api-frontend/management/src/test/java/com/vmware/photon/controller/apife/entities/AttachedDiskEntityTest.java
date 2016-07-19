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

import com.vmware.photon.controller.api.model.PersistentDisk;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

/**
 * Tests {@link ResourceTicketEntity}.
 */
public class AttachedDiskEntityTest {

  private AttachedDiskEntity attachedDiskEntity;

  @BeforeMethod
  public void setUp() {
    attachedDiskEntity = new AttachedDiskEntity();
  }

  @Test
  public void testPersistentDiskSuccessful() {
    attachedDiskEntity.setKind(PersistentDisk.KIND);
    attachedDiskEntity.setBootDisk(true);

    VmEntity vm = new VmEntity();
    vm.setId(UUID.randomUUID().toString());
    attachedDiskEntity.setVmId(vm.getId());


    BaseDiskEntity disk1 = new PersistentDiskEntity();
    disk1.setName("PersistentDisk-1");
    disk1.setId("disk1");
    attachedDiskEntity.setUnderlyingDiskIdAndKind(disk1);

    assertThat(attachedDiskEntity.getKind(), equalTo(PersistentDisk.KIND));
    assertThat(attachedDiskEntity.isBootDisk(), is(true));

    assertThat(attachedDiskEntity.getVmId(), is(vm.getId()));

    assertThat(attachedDiskEntity.getUnderlyingDiskId(), is(disk1.getId()));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNoUnderlyingDiskGetId() {
    attachedDiskEntity.getUnderlyingDiskId();
  }
}
