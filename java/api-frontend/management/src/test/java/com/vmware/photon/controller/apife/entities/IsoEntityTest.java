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

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link IsoEntity}.
 */
public class IsoEntityTest {

  @Test
  public void testCreate() {
    VmEntity vmEntity = new VmEntity();
    vmEntity.setId("vm-id1");
    vmEntity.setName("vm-1");
    vmEntity.setAgent("agent-1");
    vmEntity.setHost("1.1.1.1");
    vmEntity.setDefaultGateway("gateway-1");
    vmEntity.setDatastore("datastore-1");

    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setId("iso-id");
    isoEntity.setName("iso-name");
    isoEntity.setSize(new Long(100000));
    isoEntity.setVm(vmEntity);

    assertThat(isoEntity.getId(), is("iso-id"));
    assertThat(isoEntity.getName(), is("iso-name"));
    assertThat(isoEntity.getSize(), is(new Long(100000)));
  }
}
