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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.Datastore;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.apife.clients.DatastoreFeClient;
import com.vmware.photon.controller.apife.resources.routes.DatastoreResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DatastoresResource}.
 */
public class DatastoresResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(DatastoresResourceTest.class);

  private String datastoreRoutePath = UriBuilder.fromPath(DatastoreResourceRoutes.DATASTORE_PATH)
      .build("ds1")
      .toString();

  @Mock
  private DatastoreFeClient datastoreFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DatastoresResource(datastoreFeClient));
  }

  @Test
  public void testGetEmptyDatastoreList() {
    ResourceList<Datastore> resourceList = new ResourceList<>(new ArrayList<Datastore>());
    doReturn(resourceList).when(datastoreFeClient).find(Optional.<String>absent());

    Response clientResponse = client()
        .target(DatastoreResourceRoutes.API)
        .request()
        .accept("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Datastore> datastoreResourceList = clientResponse.readEntity(new
                                                                   GenericType<ResourceList<Datastore>>() {});
    assertThat(datastoreResourceList.getItems().size(), is(0));
  }

  @Test
  public void testGetAllDatastoreList() throws URISyntaxException {
    Datastore datastore = new Datastore();
    datastore.setId("ds1");
    datastore.setSelfLink("self link");
    datastore.setTags(ImmutableSet.of("tag1"));

    List<Datastore> datastoreList = new ArrayList<>();
    datastoreList.add(datastore);

    ResourceList<Datastore> datastoreResourceList = new ResourceList<>(datastoreList);
    doReturn(datastoreResourceList).when(datastoreFeClient).find(Optional.<String>absent());

    Response clientResponse = client()
        .target(DatastoreResourceRoutes.API)
        .request()
        .accept("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<Datastore> resourceList = clientResponse.readEntity(new GenericType<ResourceList<Datastore>>() {});
    assertThat(resourceList.getItems().size(), is(1));

    Datastore retrievedDatastore = resourceList.getItems().get(0);
    assertThat(retrievedDatastore, is(datastore));

    assertThat(new URI(retrievedDatastore.getSelfLink()).isAbsolute(), is(true));
    assertThat(retrievedDatastore.getSelfLink().endsWith(datastoreRoutePath), is(true));
  }
}
