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

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link PersistentDiskEntity}.
 */
public class PersistentDiskEntityTest {
  @Test
  public void testGetAffinitiesWithKind() {
    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setAffinities(null);
    assertThat(disk.getAffinities("vm").isEmpty(), is(true));

    disk.setAffinities(new ArrayList<LocalityEntity>());
    assertThat(disk.getAffinities("vm").isEmpty(), is(true));

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setKind("vm");
    localityEntity.setResourceId("vm-id1");
    List<LocalityEntity> affinities = ImmutableList.of(localityEntity);
    disk.setAffinities(affinities);

    assertThat(disk.getAffinities("disk").isEmpty(), is(true));
    assertThat(disk.getAffinities("vm"), is((List<String>) ImmutableList.of("vm-id1")));
  }

  @Test
  public void testGetAffinity() {
    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setAffinities(null);
    assertThat(disk.getAffinity("vm"), nullValue());

    disk.setAffinities(new ArrayList<LocalityEntity>());
    assertThat(disk.getAffinity("vm"), nullValue());

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setKind("vm");
    localityEntity.setResourceId("vm-id1");
    List<LocalityEntity> affinities = ImmutableList.of(localityEntity);
    disk.setAffinities(affinities);

    assertThat(disk.getAffinity("disk"), nullValue());
    assertThat(disk.getAffinity("vm"), is("vm-id1"));
  }

}
