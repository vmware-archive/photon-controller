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

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.clients.DatastoreFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.resources.datastore.DatastoresResource;
import com.vmware.photon.controller.api.frontend.resources.routes.DatastoreResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Datastore;
import com.vmware.photon.controller.api.model.ResourceList;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.datastore.DatastoresResource}.
 */
public class DatastoresResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(DatastoresResourceTest.class);

  private String datastoreRoutePath = UriBuilder.fromPath(DatastoreResourceRoutes.DATASTORE_PATH)
      .build("ds1")
      .toString();

  @Mock
  private DatastoreFeClient datastoreFeClient;
  private PaginationConfig paginationConfig = new PaginationConfig();
  private String datastore1Link = "";
  private String datastore2Link = "";
  private Datastore datastore1 = new Datastore();
  private Datastore datastore2 = new Datastore();

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DatastoresResource(datastoreFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    paginationConfig.setDefaultPageSize(10);
    paginationConfig.setMaxPageSize(100);

    datastore1.setId("ds1");
    datastore1.setTags(ImmutableSet.of("tag1"));

    datastore2.setId("ds2");
    datastore2.setTags(ImmutableSet.of("tag2"));

    datastore1Link = UriBuilder.fromPath(DatastoreResourceRoutes.DATASTORE_PATH).build(datastore1.getId()).toString();
    datastore2Link = UriBuilder.fromPath(DatastoreResourceRoutes.DATASTORE_PATH).build(datastore2.getId()).toString();
    datastore1.setSelfLink(datastore1Link);
    datastore2.setSelfLink(datastore2Link);
  }


  @Test
  public void testGetEmptyDatastoreList() throws Throwable {
    ResourceList<Datastore> resourceList = new ResourceList<>(new ArrayList<Datastore>());
    doReturn(resourceList).when(datastoreFeClient).find(Optional.<String>absent(), Optional.of(10));

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
  public void testGetAllDatastoreList() throws Throwable {
    Datastore datastore = new Datastore();
    datastore.setId("ds1");
    datastore.setSelfLink("self link");
    datastore.setTags(ImmutableSet.of("tag1"));

    List<Datastore> datastoreList = new ArrayList<>();
    datastoreList.add(datastore);

    ResourceList<Datastore> datastoreResourceList = new ResourceList<>(datastoreList);
    doReturn(datastoreResourceList).when(datastoreFeClient).find(Optional.<String>absent(), Optional.of(10));

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

  @Test
  public void testGetDatastoresPage() throws Exception {
    ResourceList<Datastore> expectedDatastoresPage = new ResourceList<>(ImmutableList.of(datastore1, datastore2),
            UUID.randomUUID().toString(), UUID.randomUUID().toString());
    when(datastoreFeClient.getDatastoresPage(anyString())).thenReturn(expectedDatastoresPage);

    List<String> expectedSelfLinks = ImmutableList.of(datastore1Link, datastore2Link);

    Response response = getDatastores(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Datastore> datastores = response.readEntity(
            new GenericType<ResourceList<Datastore>>() {
            }
    );

    assertThat(datastores.getItems().size(), is(expectedDatastoresPage.getItems().size()));

    for (int i = 0; i < datastores.getItems().size(); i++) {
      assertThat(new URI(datastores.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(datastores.getItems().get(i), is(expectedDatastoresPage.getItems().get(i)));
      assertThat(datastores.getItems().get(i).getSelfLink().endsWith(expectedSelfLinks.get(i)), is(true));
    }

    verifyPageLinks(datastores);
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getDatastores(Optional.<String>absent(), Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidPageSize"));
    assertThat(errors.getMessage(), is("The page size '200' is not between '1' and '100'"));
  }

  private Response getDatastores(Optional<String> tag, Optional<Integer> pageSize) {
    String uri = DatastoreResourceRoutes.API + "?tag=" + tag;
    if (pageSize.isPresent()) {
      uri += "&pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getDatastores(String pageLink) {
    String uri = DatastoreResourceRoutes.API + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Datastore> resourceList) {
    String expectedPrefix = DatastoreResourceRoutes.API + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }
}
