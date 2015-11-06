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

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.NetworkDao;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link NetworkSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class NetworkSqlBackendTest extends BaseDaoTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  private NetworkDao networkDao;

  @Inject
  private NetworkBackend networkBackend;

  private NetworkCreateSpec spec;

  private NetworkCreateSpec createNetworkCreateSpec() {
    NetworkCreateSpec spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    List<String> portGroups = new ArrayList<>();
    portGroups.add("PG1");
    portGroups.add("PG2");
    spec.setPortGroups(portGroups);
    return spec;
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    spec.setPortGroups(ImmutableList.of("PG1", "PG2"));
  }

  @Test
  public void testCreateNetworkSuccess() throws Exception {
    TaskEntity taskEntity = networkBackend.createNetwork(spec);
    String networkId = taskEntity.getEntityId();

    NetworkEntity network = networkDao.findById(networkId).get();
    assertThat(network.getName(), is(spec.getName()));
    assertThat(network.getDescription(), is(spec.getDescription()));
    assertThat(network.getPortGroups(),
        is(objectMapper.writeValueAsString((spec.getPortGroups()))));
  }

  @Test
  public void testCreateWithSameName() throws Exception {
    NetworkCreateSpec spec1 = createNetworkCreateSpec();
    networkBackend.createNetwork(spec1);
    NetworkCreateSpec spec2 = createNetworkCreateSpec();
    spec2.setPortGroups(new ArrayList<>());
    networkBackend.createNetwork(spec2);

    List<Network> networks = networkBackend.filter(
        Optional.fromNullable(spec1.getName()), Optional.<String>absent());
    assertThat(networks.size(), is(2));
  }

  @Test
  public void testPortGroupAlreadyAddedToNetworkException() throws Exception {
    NetworkCreateSpec spec = createNetworkCreateSpec();
    networkBackend.createNetwork(spec);

    try {
      networkBackend.createNetwork(spec);
      fail("create network should fail");
    } catch (PortGroupsAlreadyAddedToNetworkException ex) {
      assertThat(ex.getMessage(), containsString("Port group PG1 is already added to network Network{id="));
      assertThat(ex.getMessage(), containsString("Port group PG2 is already added to network Network{id="));
    }
  }

  @Test
  public void testFilterNetworks() throws Exception {
    networkBackend.createNetwork(spec);

    List<Network> networks = networkBackend.filter(
        Optional.of(spec.getName()), Optional.<String>absent());
    assertThat(networks.size(), is(1));
    assertThat(networks.get(0).getName(), is(spec.getName()));
    assertThat(networks.get(0).getDescription(), is(spec.getDescription()));
    assertThat(networks.get(0).getPortGroups(), is(spec.getPortGroups()));

    networks = networkBackend.filter(
        Optional.of("n2"), Optional.<String>absent());
    assertThat(networks.isEmpty(), is(true));

    networks = networkBackend.filter(
        Optional.<String>absent(), Optional.<String>absent());
    assertThat(networks.size(), is(1));

    networks = networkBackend.filter(Optional.<String>absent(), Optional.of("PG1"));
    assertThat(networks.size(), is(1));

    networks = networkBackend.filter(Optional.<String>absent(), Optional.of("foo"));
    assertThat(networks.isEmpty(), is(true));

    networks = networkBackend.filter(Optional.of(spec.getName()), Optional.of("PG2"));
    assertThat(networks.size(), is(1));
  }

  @Test
  public void testToApiRepresentation() throws Exception {
    NetworkEntity networkEntity = new NetworkEntity();
    networkEntity.setName("network1");
    networkEntity.setPortGroups("[\"PG1\"]");

    networkDao.create(networkEntity);
    String id = networkEntity.getId();

    flushSession();

    Network network = networkBackend.toApiRepresentation(id);
    assertThat(network.getId(), is(id));
    assertThat(network.getName(), is("network1"));
    assertThat(network.getPortGroups(), is((List<String>) ImmutableList.of("PG1")));
  }
}
