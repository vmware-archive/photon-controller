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

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.FlavorFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.resources.flavor.FlavorsResource;
import com.vmware.photon.controller.apife.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.flavor.FlavorsResource}.
 */
public class FlavorsResourceTest extends ResourceTest {

  @Mock
  private FlavorFeClient flavorFeClient;

  private FlavorCreateSpec spec;

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  private String flavor1Link = "";
  private String flavor2Link = "";

  private Flavor flavor1 = new Flavor();
  private Flavor flavor2 = new Flavor();
  private PaginationConfig paginationConfig = new PaginationConfig();

  @Override
  protected void setUpResources() throws Exception {
    spec = new FlavorCreateSpec();
    spec.setName("test-100");
    spec.setKind("vm");
    spec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));

    addResource(new FlavorsResource(flavorFeClient, paginationConfig));
  }

  @BeforeMethod
  public void setUp() throws Throwable {
    paginationConfig.setDefaultPageSize(10);
    paginationConfig.setMaxPageSize(100);

    flavor1.setId("f1");
    flavor1.setName("small");
    flavor1.setKind("vm");

    flavor2.setId("f2");
    flavor2.setName("small");
    flavor2.setKind("vm");

    flavor1Link = UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_PATH).build(flavor1.getId()).toString();
    flavor2Link = UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_PATH).build(flavor2.getId()).toString();
    flavor1.setSelfLink(flavor1Link);
    flavor2.setSelfLink(flavor2Link);

    when(
            flavorFeClient.list(Optional.of(flavor1.getName()), Optional.of(flavor1.getKind()),
                    Optional.of(10))
    ).thenReturn(new ResourceList<>(ImmutableList.of(flavor1, flavor2), null, null));
    when(
            flavorFeClient.list(Optional.of(flavor1.getName()), Optional.of(flavor1.getKind()),
                    Optional.of(2))
    ).thenReturn(new ResourceList<>(ImmutableList.of(flavor1, flavor2), null, null));
    when(
            flavorFeClient.list(Optional.of(flavor1.getName()), Optional.of(flavor1.getKind()),
                    Optional.of(1))
    ).thenReturn(new ResourceList<>(ImmutableList.of(flavor1), UUID.randomUUID().toString(), null));
    when(
            flavorFeClient.list(Optional.of(flavor1.getName()), Optional.of(flavor1.getKind()),
                    Optional.of(3))
    ).thenReturn(new ResourceList<>(Collections.emptyList(), null, null));
  }

  @Test
  public void testSuccessfulCreateFlavor() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(flavorFeClient.create(spec)).thenReturn(task);

    Response response = createFlavor(spec);

    assertThat(response.getStatus(), is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testFailedCreateFlavor() throws Exception {
    when(flavorFeClient.create(spec)).thenThrow(new ExternalException("failed"));
    assertThat(createFlavor(spec).getStatus(), is(500));
  }

  @Test
  public void testFailedCreateFlavorWithInvalidKind() throws Exception {
    FlavorCreateSpec wrongKindSpec = new FlavorCreateSpec();
    wrongKindSpec.setName("test-100");
    wrongKindSpec.setKind("disk");
    wrongKindSpec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));

    assertThat(createFlavor(wrongKindSpec).getStatus(), is(400));
  }

  @Test(dataProvider = "pageSizes")
  public void testFilterFlavors(Optional<Integer> pageSize,
                              List<Flavor> expectedFlavors,
                              List<String> expectedLinks) throws Exception {

    Response response = getFlavors(flavor1.getName(), flavor1.getKind(), pageSize);
    assertThat(response.getStatus(), is(200));

    ResourceList<Flavor> flavors = response.readEntity(
            new GenericType<ResourceList<Flavor>>() {
            }
    );

    for (int i = 0; i < flavors.getItems().size(); i++) {
      assertThat(new URI(flavors.getItems().get(i).getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(flavors.getItems().get(i), is(expectedFlavors.get(i)));
      assertThat(flavors.getItems().get(i).getSelfLink().endsWith(expectedLinks.get(i)), is(true));
    }

    verifyPageLinks(flavors);
  }

  @Test
  public void testGetFlavorsPage() throws Exception {
    ResourceList<Flavor> expectedFlavorsPage = new ResourceList<>(ImmutableList.of(flavor1, flavor2),
            UUID.randomUUID().toString(), UUID.randomUUID().toString());
    when(flavorFeClient.getFlavorsPage(anyString())).thenReturn(expectedFlavorsPage);

    List<String> expectedSelfLinks = ImmutableList.of(flavor1Link, flavor2Link);

    Response response = getFlavors(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Flavor> flavors = response.readEntity(
            new GenericType<ResourceList<Flavor>>() {
            }
    );

    assertThat(flavors.getItems().size(), is(expectedFlavorsPage.getItems().size()));

    for (int i = 0; i < flavors.getItems().size(); i++) {
      assertThat(new URI(flavors.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(flavors.getItems().get(i), is(expectedFlavorsPage.getItems().get(i)));
      assertThat(flavors.getItems().get(i).getSelfLink().endsWith(expectedSelfLinks.get(i)), is(true));
    }

    verifyPageLinks(flavors);
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getFlavors(flavor1.getName(), flavor1.getKind(), Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidPageSize"));
    assertThat(errors.getMessage(), is("The page size '200' is not between '1' and '100'"));
  }

  private Response createFlavor(FlavorCreateSpec spec) {
    return client()
        .target(FlavorsResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][] {
      {
        Optional.<Integer>absent(),
        ImmutableList.of(flavor1, flavor2),
        ImmutableList.of(flavor1.getSelfLink(), flavor2.getSelfLink())
      },
      {
        Optional.of(1),
        ImmutableList.of(flavor1),
        ImmutableList.of(flavor1.getSelfLink())
      },
      {
        Optional.of(2),
        ImmutableList.of(flavor1, flavor2),
        ImmutableList.of(flavor1.getSelfLink(), flavor2.getSelfLink())
      },
      {
        Optional.of(3),
        Collections.emptyList(),
        Collections.emptyList()
      }
    };
  }

  private Response getFlavors(String name, String kind, Optional<Integer> pageSize) {
    String uri = FlavorsResourceRoutes.API + "?name=" + name + "&kind=" + kind;
    if (pageSize.isPresent()) {
      uri += "&pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getFlavors(String pageLink) {
    String uri = FlavorsResourceRoutes.API + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Flavor> resourceList) {
    String expectedPrefix = FlavorsResourceRoutes.API + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }
}
