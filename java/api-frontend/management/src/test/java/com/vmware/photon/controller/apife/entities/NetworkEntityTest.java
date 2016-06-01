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

import com.vmware.photon.controller.api.NetworkState;

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
        new Object[]{null, NetworkState.CREATING},
        new Object[]{null, NetworkState.READY},
        new Object[]{null, NetworkState.PENDING_DELETE},
        new Object[]{null, NetworkState.ERROR},
        new Object[]{null, NetworkState.DELETED},
        new Object[]{NetworkState.CREATING, null},
        new Object[]{NetworkState.CREATING, NetworkState.READY},
        new Object[]{NetworkState.CREATING, NetworkState.ERROR},
        new Object[]{NetworkState.CREATING, NetworkState.DELETED},
        new Object[]{NetworkState.READY, null},
        new Object[]{NetworkState.READY, NetworkState.PENDING_DELETE},
        new Object[]{NetworkState.READY, NetworkState.ERROR},
        new Object[]{NetworkState.READY, NetworkState.DELETED},
        new Object[]{NetworkState.PENDING_DELETE, NetworkState.ERROR},
        new Object[]{NetworkState.PENDING_DELETE, NetworkState.DELETED},
        new Object[]{NetworkState.ERROR, null},
        new Object[]{NetworkState.ERROR, NetworkState.DELETED},
        new Object[]{NetworkState.DELETED, null}
    };
  }

  @Test(dataProvider = "getSetValidStateParams")
  public void testSetValidState(NetworkState originalState, NetworkState newState) throws Exception {
    NetworkEntity network = new NetworkEntity();
    network.setState(originalState);
    network.setState(newState);
  }

  @DataProvider(name = "getSetInvalidStateParams")
  public Object[][] getSetInvalidStateParams() {
    return new Object[][]{
        new Object[]{NetworkState.CREATING, NetworkState.PENDING_DELETE},
        new Object[]{NetworkState.READY, NetworkState.CREATING},
        new Object[]{NetworkState.PENDING_DELETE, NetworkState.CREATING},
        new Object[]{NetworkState.PENDING_DELETE, NetworkState.READY},
        new Object[]{NetworkState.ERROR, NetworkState.CREATING},
        new Object[]{NetworkState.ERROR, NetworkState.READY},
        new Object[]{NetworkState.DELETED, NetworkState.CREATING},
        new Object[]{NetworkState.DELETED, NetworkState.READY},
        new Object[]{NetworkState.DELETED, NetworkState.ERROR}
    };
  }

  @Test(dataProvider = "getSetInvalidStateParams")
  public void testSetInvalidState(NetworkState originalState, NetworkState newState) throws Exception {
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
