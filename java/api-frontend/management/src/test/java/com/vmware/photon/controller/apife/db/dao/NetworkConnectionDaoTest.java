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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.NetworkConnectionEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link NetworkConnectionDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class NetworkConnectionDaoTest extends BaseDaoTest {
  @Inject
  private NetworkConnectionDao networkConnectionDao;

  @Test
  public void testCreate() throws Exception {
    NetworkConnectionEntity networkConnection = createNetworkConnection("network-1", "10.146.30.120");
    String id = networkConnection.getId();

    flushSession();

    Optional<NetworkConnectionEntity> found = networkConnectionDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getNetwork(), equalTo("network-1"));
    assertThat(found.get().getIpAddress(), equalTo("10.146.30.120"));
  }

  private NetworkConnectionEntity createNetworkConnection(String networkName, String ipAddress) throws Exception {
    NetworkConnectionEntity networkConnectionEntity = new NetworkConnectionEntity(networkName, ipAddress,
        "255.255.255.0");
    networkConnectionDao.create(networkConnectionEntity);
    return networkConnectionEntity;
  }
}
