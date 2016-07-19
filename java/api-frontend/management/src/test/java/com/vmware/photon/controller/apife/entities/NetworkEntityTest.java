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

import com.vmware.photon.controller.api.model.SubnetState;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * Tests {@link NetworkEntity}.
 */
public class NetworkEntityTest {

  private NetworkEntity networkEntity;

  @BeforeMethod
  public void setUp() {
    networkEntity = new NetworkEntity();
  }

  @Test
  public void testNetworkSuccessful() {
    networkEntity.setName("network1");
    networkEntity.setDescription("VLAN");
    networkEntity.setPortGroups("[\"PG1\",\"PG2\"]");
    networkEntity.setIsDefault(false);

    assertThat(networkEntity.getName(), is("network1"));
    assertThat(networkEntity.getDescription(), is("VLAN"));
    assertThat(networkEntity.getPortGroups(), is("[\"PG1\",\"PG2\"]"));
    assertThat(networkEntity.getIsDefault(), is(false));
  }

  @DataProvider(name = "getSetValidStateParams")
  public Object[][] getSetValidStateParams() {
    return new Object[][]{
        new Object[]{null, SubnetState.CREATING},
        new Object[]{null, SubnetState.READY},
        new Object[]{null, SubnetState.PENDING_DELETE},
        new Object[]{null, SubnetState.ERROR},
        new Object[]{null, SubnetState.DELETED},
        new Object[]{SubnetState.CREATING, null},
        new Object[]{SubnetState.CREATING, SubnetState.READY},
        new Object[]{SubnetState.CREATING, SubnetState.ERROR},
        new Object[]{SubnetState.CREATING, SubnetState.DELETED},
        new Object[]{SubnetState.READY, null},
        new Object[]{SubnetState.READY, SubnetState.PENDING_DELETE},
        new Object[]{SubnetState.READY, SubnetState.ERROR},
        new Object[]{SubnetState.READY, SubnetState.DELETED},
        new Object[]{SubnetState.PENDING_DELETE, SubnetState.ERROR},
        new Object[]{SubnetState.PENDING_DELETE, SubnetState.DELETED},
        new Object[]{SubnetState.ERROR, null},
        new Object[]{SubnetState.ERROR, SubnetState.DELETED},
        new Object[]{SubnetState.DELETED, null}
    };
  }

  @Test(dataProvider = "getSetValidStateParams")
  public void testSetValidState(SubnetState originalState, SubnetState newState) throws Exception {
    NetworkEntity network = new NetworkEntity();
    network.setState(originalState);
    network.setState(newState);
  }

  @DataProvider(name = "getSetInvalidStateParams")
  public Object[][] getSetInvalidStateParams() {
    return new Object[][]{
        new Object[]{SubnetState.CREATING, SubnetState.PENDING_DELETE},
        new Object[]{SubnetState.READY, SubnetState.CREATING},
        new Object[]{SubnetState.PENDING_DELETE, SubnetState.CREATING},
        new Object[]{SubnetState.PENDING_DELETE, SubnetState.READY},
        new Object[]{SubnetState.ERROR, SubnetState.CREATING},
        new Object[]{SubnetState.ERROR, SubnetState.READY},
        new Object[]{SubnetState.DELETED, SubnetState.CREATING},
        new Object[]{SubnetState.DELETED, SubnetState.READY},
        new Object[]{SubnetState.DELETED, SubnetState.ERROR}
    };
  }

  @Test(dataProvider = "getSetInvalidStateParams")
  public void testSetInvalidState(SubnetState originalState, SubnetState newState) throws Exception {
    NetworkEntity network = new NetworkEntity();
    network.setState(originalState);

    try {
      network.setState(newState);
      fail("setState should throw exception");
    } catch (IllegalStateException ex) {
      assertThat(ex.getMessage(),
          is(String.format("%s -> %s", originalState, newState)));
    }
  }
}
